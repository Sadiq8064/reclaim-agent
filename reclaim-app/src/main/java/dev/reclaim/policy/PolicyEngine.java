package dev.reclaim.policy;

import dev.reclaim.domain.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class PolicyEngine {

    private final AtomicBoolean killSwitchHalted = new AtomicBoolean(false);

    private final int maxRetries;
    private final int minRetryIntervalHours;
    private final int maxContacts;
    private final int contactCooldownHours;
    private final int perCaseSpendCapPercent;
    private final long highValueThresholdPaise;
    private final long runSpendCapPaise;

    private static final ZoneId IST_ZONE = ZoneId.of("Asia/Kolkata");

    public PolicyEngine(
            @Value("${reclaim.policy.max-retries:3}") int maxRetries,
            @Value("${reclaim.policy.min-retry-interval-hours:6}") int minRetryIntervalHours,
            @Value("${reclaim.policy.max-contacts:3}") int maxContacts,
            @Value("${reclaim.policy.contact-cooldown-hours:24}") int contactCooldownHours,
            @Value("${reclaim.policy.per-case-spend-cap-percent:15}") int perCaseSpendCapPercent,
            @Value("${reclaim.policy.high-value-threshold-paise:1000000}") long highValueThresholdPaise,
            @Value("${reclaim.policy.run-spend-cap-paise:10000000}") long runSpendCapPaise) {
        this.maxRetries = maxRetries;
        this.minRetryIntervalHours = minRetryIntervalHours;
        this.maxContacts = maxContacts;
        this.contactCooldownHours = contactCooldownHours;
        this.perCaseSpendCapPercent = perCaseSpendCapPercent;
        this.highValueThresholdPaise = highValueThresholdPaise;
        this.runSpendCapPaise = runSpendCapPaise;
    }

    public void haltExecution() {
        killSwitchHalted.set(true);
    }

    public void resumeExecution() {
        killSwitchHalted.set(false);
    }

    public boolean isHalted() {
        return killSwitchHalted.get();
    }

    public record ProposedAction(
            ActionType actionType,
            Instant scheduledFor,
            long estimatedCostPaise,
            String channel,
            String message,
            String reason,
            String bankMethod
    ) {}

    public record CustomerContext(
            boolean optedOut,
            boolean subscriptionCancelled,
            Instant lastRetryTime,
            Instant lastContactTime,
            boolean methodInDowntime,
            Instant downtimeResolvesAt,
            long totalRunSpendPaise,
            Set<String> pendingActionKeys
    ) {}

    public record EvaluationResult(
            VerdictType verdict,
            ProposedAction finalAction,
            String violatedRule,
            String reason,
            List<String> passedRules
    ) {}

    /**
     * Pure deterministic evaluation function.
     */
    public EvaluationResult evaluate(
            ProposedAction proposal,
            RecoveryCase recoveryCase,
            CustomerContext context,
            Instant now) {

        List<String> passedRules = new ArrayList<>();

        // 0. Kill Switch check
        if (killSwitchHalted.get()) {
            return new EvaluationResult(VerdictType.DENY, proposal, "KILL_SWITCH_ACTIVE",
                    "Global admin emergency kill switch is active. All recovery actions are halted.", passedRules);
        }
        passedRules.add("KILL_SWITCH_CHECK");

        // 1. TERMINAL_STATE_LOCK
        if (recoveryCase.getState() != null && recoveryCase.getState().isTerminal()) {
            return new EvaluationResult(VerdictType.DENY, proposal, "TERMINAL_STATE_LOCK",
                    "Case is in terminal state " + recoveryCase.getState() + ". No further actions permitted.", passedRules);
        }
        passedRules.add("TERMINAL_STATE_LOCK");

        // 2. CANCELLED_SUB_LOCK
        if (context.subscriptionCancelled() || "CUSTOMER_CHURNED".equalsIgnoreCase(recoveryCase.getFailureCode())) {
            return new EvaluationResult(VerdictType.DENY, proposal, "CANCELLED_SUB_LOCK",
                    "Subscription was cancelled or customer has churned.", passedRules);
        }
        passedRules.add("CANCELLED_SUB_LOCK");

        // 3. RUN_SPEND_CAP
        if (context.totalRunSpendPaise() + proposal.estimatedCostPaise() > runSpendCapPaise) {
            return new EvaluationResult(VerdictType.DENY, proposal, "RUN_SPEND_CAP",
                    "Total run spend cap reached (" + runSpendCapPaise + " paise).", passedRules);
        }
        passedRules.add("RUN_SPEND_CAP");

        // 4. PER_CASE_SPEND_CAP
        long maxCaseSpendPaise = (recoveryCase.getAmountPaise() * perCaseSpendCapPercent) / 100;
        if (recoveryCase.getCostIncurredPaise() + proposal.estimatedCostPaise() > maxCaseSpendPaise) {
            return new EvaluationResult(VerdictType.DENY, proposal, "PER_CASE_SPEND_CAP",
                    "Action cost exceeds per-case spend cap of " + perCaseSpendCapPercent + "% (" + maxCaseSpendPaise + " paise).", passedRules);
        }
        passedRules.add("PER_CASE_SPEND_CAP");

        // 5. HIGH_VALUE_APPROVAL
        if (recoveryCase.getAmountPaise() >= highValueThresholdPaise &&
                (proposal.actionType() == ActionType.SCHEDULE_RETRY || proposal.actionType() == ActionType.CREATE_PAYMENT_LINK)) {
            ProposedAction escalated = new ProposedAction(ActionType.ESCALATE, now, 4000L, null, null,
                    "High value case (>= ₹" + (highValueThresholdPaise / 100) + ") requires manual analyst review", proposal.bankMethod());
            return new EvaluationResult(VerdictType.MODIFY, escalated, "HIGH_VALUE_APPROVAL",
                    "High value recovery requires human escalation approval.", passedRules);
        }
        passedRules.add("HIGH_VALUE_APPROVAL");

        // 6. IDEMPOTENCY_GUARD
        String actionKey = proposal.actionType().name() + "::" + recoveryCase.getId();
        if (context.pendingActionKeys() != null && context.pendingActionKeys().contains(actionKey)) {
            return new EvaluationResult(VerdictType.DENY, proposal, "IDEMPOTENCY_GUARD",
                    "Identical action is already pending execution.", passedRules);
        }
        passedRules.add("IDEMPOTENCY_GUARD");

        // Specific checks for SCHEDULE_RETRY
        if (proposal.actionType() == ActionType.SCHEDULE_RETRY) {
            // 7. MAX_RETRIES
            if (recoveryCase.getAttemptCount() >= maxRetries) {
                return new EvaluationResult(VerdictType.DENY, proposal, "MAX_RETRIES",
                        "Maximum retry cap of " + maxRetries + " reached.", passedRules);
            }
            passedRules.add("MAX_RETRIES");

            // 8. DOWNTIME_BLOCK
            if (context.methodInDowntime() && context.downtimeResolvesAt() != null) {
                if (proposal.scheduledFor().isBefore(context.downtimeResolvesAt())) {
                    ProposedAction modified = new ProposedAction(
                            proposal.actionType(),
                            context.downtimeResolvesAt().plus(Duration.ofMinutes(15)),
                            proposal.estimatedCostPaise(),
                            proposal.channel(),
                            proposal.message(),
                            "Modified to execute after bank downtime clears at " + context.downtimeResolvesAt(),
                            proposal.bankMethod()
                    );
                    return new EvaluationResult(VerdictType.MODIFY, modified, "DOWNTIME_BLOCK",
                            "Payment method is in downtime. Postponed retry to after downtime window.", passedRules);
                }
            }
            passedRules.add("DOWNTIME_BLOCK");

            // 9. MIN_RETRY_INTERVAL
            if (context.lastRetryTime() != null) {
                Instant minLegalTime = context.lastRetryTime().plus(Duration.ofHours(minRetryIntervalHours));
                if (proposal.scheduledFor().isBefore(minLegalTime)) {
                    ProposedAction modified = new ProposedAction(
                            proposal.actionType(),
                            minLegalTime,
                            proposal.estimatedCostPaise(),
                            proposal.channel(),
                            proposal.message(),
                            "Adjusted to satisfy minimum " + minRetryIntervalHours + "h retry interval",
                            proposal.bankMethod()
                    );
                    return new EvaluationResult(VerdictType.MODIFY, modified, "MIN_RETRY_INTERVAL",
                            "Retry interval was less than " + minRetryIntervalHours + " hours.", passedRules);
                }
            }
            passedRules.add("MIN_RETRY_INTERVAL");
        }

        // Specific checks for SEND_MESSAGE
        if (proposal.actionType() == ActionType.SEND_MESSAGE) {
            // 10. CONSENT_CHECK
            if (context.optedOut()) {
                return new EvaluationResult(VerdictType.DENY, proposal, "CONSENT_CHECK",
                        "Customer has opted out of communication.", passedRules);
            }
            passedRules.add("CONSENT_CHECK");

            // 11. MAX_CONTACTS
            if (recoveryCase.getContactCount() >= maxContacts) {
                return new EvaluationResult(VerdictType.DENY, proposal, "MAX_CONTACTS",
                        "Maximum customer contacts limit (" + maxContacts + ") reached.", passedRules);
            }
            passedRules.add("MAX_CONTACTS");

            // 12. CONTACT_COOLDOWN
            if (context.lastContactTime() != null) {
                Instant minLegalContact = context.lastContactTime().plus(Duration.ofHours(contactCooldownHours));
                if (proposal.scheduledFor().isBefore(minLegalContact)) {
                    ProposedAction modified = new ProposedAction(
                            proposal.actionType(),
                            minLegalContact,
                            proposal.estimatedCostPaise(),
                            proposal.channel(),
                            proposal.message(),
                            "Adjusted for 24h contact cooldown",
                            proposal.bankMethod()
                    );
                    return new EvaluationResult(VerdictType.MODIFY, modified, "CONTACT_COOLDOWN",
                            "Contact interval was less than " + contactCooldownHours + " hours.", passedRules);
                }
            }
            passedRules.add("CONTACT_COOLDOWN");

            // 13. QUIET_HOURS (21:00 - 09:00 IST)
            ZonedDateTime istTime = proposal.scheduledFor().atZone(IST_ZONE);
            int hour = istTime.getHour();
            if (hour >= 21 || hour < 9) {
                ZonedDateTime nextMorning = istTime.withHour(9).withMinute(0).withSecond(0).withNano(0);
                if (hour >= 21) {
                    nextMorning = nextMorning.plusDays(1);
                }
                ProposedAction modified = new ProposedAction(
                        proposal.actionType(),
                        nextMorning.toInstant(),
                        proposal.estimatedCostPaise(),
                        proposal.channel(),
                        proposal.message(),
                        "Adjusted to comply with IST quiet hours (09:00-21:00 only)",
                        proposal.bankMethod()
                );
                return new EvaluationResult(VerdictType.MODIFY, modified, "QUIET_HOURS",
                        "Scheduled time fell inside TRAI/IST quiet hours (21:00-09:00).", passedRules);
            }
            passedRules.add("QUIET_HOURS");
        }

        // All checks passed!
        return new EvaluationResult(VerdictType.ALLOW, proposal, null, "All guardrails satisfied", passedRules);
    }
}
