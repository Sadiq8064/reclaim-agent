package dev.reclaim.rules;

import dev.reclaim.domain.*;
import dev.reclaim.policy.PolicyEngine;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class RulesRecoveryEngine {

    public record PlannedRuleAction(
            ActionType actionType,
            Instant scheduledFor,
            long estimatedCostPaise,
            String channel,
            String message,
            String reason
    ) {}

    public record RulesDecision(
            String diagnosis,
            double confidence,
            String reasoning,
            List<PlannedRuleAction> plan
    ) {}

    public RulesDecision decide(RecoveryCase recoveryCase, Instant now) {
        String code = recoveryCase.getFailureCode() != null ? recoveryCase.getFailureCode().toUpperCase() : "UNKNOWN";
        List<PlannedRuleAction> plan = new ArrayList<>();

        switch (code) {
            case "INSUFFICIENT_FUNDS" -> {
                // Retry near salary window (24-48h later) + payment link
                plan.add(new PlannedRuleAction(
                        ActionType.SCHEDULE_RETRY,
                        now.plus(Duration.ofHours(24)),
                        200L,
                        null,
                        null,
                        "Schedule charge retry timed after standard salary replenishment"
                ));
                plan.add(new PlannedRuleAction(
                        ActionType.CREATE_PAYMENT_LINK,
                        now.plus(Duration.ofHours(2)),
                        0L,
                        null,
                        null,
                        "Provide instant UPI/Card alternative payment link"
                ));
                plan.add(new PlannedRuleAction(
                        ActionType.SEND_MESSAGE,
                        now.plus(Duration.ofHours(3)),
                        35L,
                        "WHATSAPP",
                        "Your subscription payment of ₹" + (recoveryCase.getAmountPaise() / 100) + " was declined due to insufficient balance. Click here to pay: {{link}}",
                        "Nudge customer with payment link"
                ));
                return new RulesDecision(
                        "Customer account temporarily lacks funds; likely recoverable after pay-cycle or via instant backup payment link.",
                        0.88,
                        "Rules heuristic: Insufficient funds responds well to timed retries combined with alternative payment links.",
                        plan
                );
            }
            case "BANK_DOWNTIME" -> {
                // Wait for downtime to clear + retry
                plan.add(new PlannedRuleAction(
                        ActionType.SCHEDULE_RETRY,
                        now.plus(Duration.ofHours(6)),
                        200L,
                        null,
                        null,
                        "Retry mandate after issuer bank outage resolves"
                ));
                return new RulesDecision(
                        "Bank gateway downtime detected. Blind retries would fail; postponing until service clears.",
                        0.92,
                        "Rules heuristic: Avoid wasted retries during verified bank downtime.",
                        plan
                );
            }
            case "CARD_EXPIRED" -> {
                // Never retry an expired card mandate; payment link only
                plan.add(new PlannedRuleAction(
                        ActionType.CREATE_PAYMENT_LINK,
                        now.plus(Duration.ofMinutes(15)),
                        0L,
                        null,
                        null,
                        "Generate payment link to allow new payment method setup"
                ));
                plan.add(new PlannedRuleAction(
                        ActionType.SEND_MESSAGE,
                        now.plus(Duration.ofMinutes(30)),
                        35L,
                        "SMS",
                        "Your card on file has expired. Please update your payment method to keep your subscription active: {{link}}",
                        "Notify customer to update payment credentials"
                ));
                return new RulesDecision(
                        "Card has permanently expired. Mandate retries are unrecoverable; customer must update credentials via payment link.",
                        0.95,
                        "Rules heuristic: 0 wasted retries on dead cards.",
                        plan
                );
            }
            case "MANDATE_REVOKED" -> {
                // Honest give up - 0 wasted actions
                plan.add(new PlannedRuleAction(
                        ActionType.CLOSE_CASE,
                        now,
                        0L,
                        null,
                        null,
                        "Mandate was explicitly revoked by customer. Zero retries attempted."
                ));
                return new RulesDecision(
                        "Customer revoked auto-debit authorization with their bank.",
                        0.99,
                        "Rules heuristic: Immediate honest abandonment to prevent wasted fees and harassment.",
                        plan
                );
            }
            case "LIMIT_EXCEEDED" -> {
                plan.add(new PlannedRuleAction(
                        ActionType.CREATE_PAYMENT_LINK,
                        now.plus(Duration.ofHours(1)),
                        0L,
                        null,
                        null,
                        "Offer split or alternative payment link"
                ));
                plan.add(new PlannedRuleAction(
                        ActionType.SCHEDULE_RETRY,
                        now.plus(Duration.ofHours(24)),
                        200L,
                        null,
                        null,
                        "Retry after daily/monthly limit reset"
                ));
                return new RulesDecision(
                        "Card or account transaction limit exceeded.",
                        0.80,
                        "Rules heuristic: Provide immediate payment link while waiting for daily limit reset.",
                        plan
                );
            }
            case "TECHNICAL_DECLINE" -> {
                plan.add(new PlannedRuleAction(
                        ActionType.SCHEDULE_RETRY,
                        now.plus(Duration.ofHours(6)),
                        200L,
                        null,
                        null,
                        "Retry after transient network glitch clears"
                ));
                return new RulesDecision(
                        "Transient network / technical decline by issuer switch.",
                        0.85,
                        "Rules heuristic: Transient technical decline responds well to clean spaced retry.",
                        plan
                );
            }
            case "CUSTOMER_CHURNED" -> {
                plan.add(new PlannedRuleAction(
                        ActionType.CLOSE_CASE,
                        now,
                        0L,
                        null,
                        null,
                        "Customer churned. Abandoning case to prevent spamming."
                ));
                return new RulesDecision(
                        "Customer has cancelled or expressed intent to churn.",
                        0.98,
                        "Rules heuristic: Zero outreach on churned users.",
                        plan
                );
            }
            default -> {
                plan.add(new PlannedRuleAction(
                        ActionType.CREATE_PAYMENT_LINK,
                        now.plus(Duration.ofHours(2)),
                        0L,
                        null,
                        null,
                        "Fallback payment link"
                ));
                return new RulesDecision(
                        "Unknown failure code; applying conservative recovery strategy.",
                        0.60,
                        "Default fallback heuristic",
                        plan
                );
            }
        }
    }
}
