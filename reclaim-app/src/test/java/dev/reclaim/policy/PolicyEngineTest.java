package dev.reclaim.policy;

import dev.reclaim.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PolicyEngineTest {

    private PolicyEngine policyEngine;
    private RecoveryCase testCase;
    private Instant now;

    @BeforeEach
    void setUp() {
        policyEngine = new PolicyEngine(3, 6, 3, 24, 15, 1000000L, 10000000L);
        now = Instant.parse("2026-08-20T10:00:00Z"); // 15:30 IST (inside business hours)
        testCase = new RecoveryCase(
                UUID.randomUUID(),
                "sub_123",
                "cust_123",
                "INV-01",
                49900L, // ₹499
                "INR",
                CaseState.DIAGNOSING,
                "INSUFFICIENT_FUNDS",
                "Balance low",
                now,
                UUID.randomUUID()
        );
    }

    private PolicyEngine.CustomerContext emptyContext() {
        return new PolicyEngine.CustomerContext(false, false, null, null, false, null, 0L, Set.of());
    }

    @Test
    @DisplayName("ALLOW legal retry proposal")
    void testAllowLegalRetry() {
        PolicyEngine.ProposedAction proposal = new PolicyEngine.ProposedAction(
                ActionType.SCHEDULE_RETRY,
                now.plus(Duration.ofHours(12)),
                200L,
                null,
                null,
                "Retry after salary cycle",
                "UPI"
        );

        PolicyEngine.EvaluationResult result = policyEngine.evaluate(proposal, testCase, emptyContext(), now);
        assertEquals(VerdictType.ALLOW, result.verdict());
        assertNull(result.violatedRule());
    }

    @Test
    @DisplayName("Rule 0: DENY when Emergency Kill Switch is active")
    void testKillSwitchHalt() {
        policyEngine.haltExecution();
        PolicyEngine.ProposedAction proposal = new PolicyEngine.ProposedAction(
                ActionType.SCHEDULE_RETRY,
                now.plus(Duration.ofHours(12)),
                200L,
                null,
                null,
                "Retry",
                "UPI"
        );

        PolicyEngine.EvaluationResult result = policyEngine.evaluate(proposal, testCase, emptyContext(), now);
        assertEquals(VerdictType.DENY, result.verdict());
        assertEquals("KILL_SWITCH_ACTIVE", result.violatedRule());

        policyEngine.resumeExecution();
        PolicyEngine.EvaluationResult resumed = policyEngine.evaluate(proposal, testCase, emptyContext(), now);
        assertEquals(VerdictType.ALLOW, resumed.verdict());
    }

    @Test
    @DisplayName("Rule 1: TERMINAL_STATE_LOCK denies actions on RECOVERED/ABANDONED/ESCALATED cases")
    void testTerminalStateLock() {
        testCase.setState(CaseState.RECOVERED);
        PolicyEngine.ProposedAction proposal = new PolicyEngine.ProposedAction(
                ActionType.SCHEDULE_RETRY,
                now.plus(Duration.ofHours(12)),
                200L,
                null,
                null,
                "Retry",
                "UPI"
        );

        PolicyEngine.EvaluationResult result = policyEngine.evaluate(proposal, testCase, emptyContext(), now);
        assertEquals(VerdictType.DENY, result.verdict());
        assertEquals("TERMINAL_STATE_LOCK", result.violatedRule());
    }

    @Test
    @DisplayName("Rule 2: CANCELLED_SUB_LOCK denies actions when subscription is cancelled")
    void testCancelledSubscriptionLock() {
        PolicyEngine.CustomerContext context = new PolicyEngine.CustomerContext(
                false, true, null, null, false, null, 0L, Set.of()
        );
        PolicyEngine.ProposedAction proposal = new PolicyEngine.ProposedAction(
                ActionType.SCHEDULE_RETRY,
                now.plus(Duration.ofHours(12)),
                200L,
                null,
                null,
                "Retry",
                "UPI"
        );

        PolicyEngine.EvaluationResult result = policyEngine.evaluate(proposal, testCase, context, now);
        assertEquals(VerdictType.DENY, result.verdict());
        assertEquals("CANCELLED_SUB_LOCK", result.violatedRule());
    }

    @Test
    @DisplayName("Rule 4: PER_CASE_SPEND_CAP denies action if cost exceeds 15% of case amount")
    void testPerCaseSpendCap() {
        testCase.setAmountPaise(10000L); // ₹100 case -> 15% is ₹15 (1500 paise)
        testCase.setCostIncurredPaise(1400L);

        PolicyEngine.ProposedAction expensiveProposal = new PolicyEngine.ProposedAction(
                ActionType.SCHEDULE_RETRY,
                now.plus(Duration.ofHours(12)),
                200L, // 1400 + 200 = 1600 > 1500
                null,
                null,
                "Retry",
                "UPI"
        );

        PolicyEngine.EvaluationResult result = policyEngine.evaluate(expensiveProposal, testCase, emptyContext(), now);
        assertEquals(VerdictType.DENY, result.verdict());
        assertEquals("PER_CASE_SPEND_CAP", result.violatedRule());
    }

    @Test
    @DisplayName("Rule 5: HIGH_VALUE_APPROVAL forces escalation for cases >= ₹10,000")
    void testHighValueApproval() {
        testCase.setAmountPaise(1500000L); // ₹15,000
        PolicyEngine.ProposedAction proposal = new PolicyEngine.ProposedAction(
                ActionType.SCHEDULE_RETRY,
                now.plus(Duration.ofHours(12)),
                200L,
                null,
                null,
                "Auto retry",
                "UPI"
        );

        PolicyEngine.EvaluationResult result = policyEngine.evaluate(proposal, testCase, emptyContext(), now);
        assertEquals(VerdictType.MODIFY, result.verdict());
        assertEquals("HIGH_VALUE_APPROVAL", result.violatedRule());
        assertEquals(ActionType.ESCALATE, result.finalAction().actionType());
    }

    @Test
    @DisplayName("Rule 6: IDEMPOTENCY_GUARD denies duplicate pending actions")
    void testIdempotencyGuard() {
        String actionKey = ActionType.SCHEDULE_RETRY.name() + "::" + testCase.getId();
        PolicyEngine.CustomerContext context = new PolicyEngine.CustomerContext(
                false, false, null, null, false, null, 0L, Set.of(actionKey)
        );

        PolicyEngine.ProposedAction proposal = new PolicyEngine.ProposedAction(
                ActionType.SCHEDULE_RETRY,
                now.plus(Duration.ofHours(12)),
                200L,
                null,
                null,
                "Duplicate retry",
                "UPI"
        );

        PolicyEngine.EvaluationResult result = policyEngine.evaluate(proposal, testCase, context, now);
        assertEquals(VerdictType.DENY, result.verdict());
        assertEquals("IDEMPOTENCY_GUARD", result.violatedRule());
    }

    @Test
    @DisplayName("Rule 7: MAX_RETRIES denies 4th charge attempt")
    void testMaxRetries() {
        testCase.setAttemptCount(3); // already 3 attempts made
        PolicyEngine.ProposedAction proposal = new PolicyEngine.ProposedAction(
                ActionType.SCHEDULE_RETRY,
                now.plus(Duration.ofHours(12)),
                200L,
                null,
                null,
                "4th retry",
                "UPI"
        );

        PolicyEngine.EvaluationResult result = policyEngine.evaluate(proposal, testCase, emptyContext(), now);
        assertEquals(VerdictType.DENY, result.verdict());
        assertEquals("MAX_RETRIES", result.violatedRule());
    }

    @Test
    @DisplayName("Rule 8: DOWNTIME_BLOCK modifies retry time to after downtime resolves")
    void testDowntimeBlock() {
        Instant downtimeResolves = now.plus(Duration.ofHours(4));
        PolicyEngine.CustomerContext context = new PolicyEngine.CustomerContext(
                false, false, null, null, true, downtimeResolves, 0L, Set.of()
        );

        PolicyEngine.ProposedAction proposal = new PolicyEngine.ProposedAction(
                ActionType.SCHEDULE_RETRY,
                now.plus(Duration.ofHours(1)), // scheduled during downtime
                200L,
                null,
                null,
                "Retry",
                "UPI"
        );

        PolicyEngine.EvaluationResult result = policyEngine.evaluate(proposal, testCase, context, now);
        assertEquals(VerdictType.MODIFY, result.verdict());
        assertEquals("DOWNTIME_BLOCK", result.violatedRule());
        assertTrue(result.finalAction().scheduledFor().isAfter(downtimeResolves));
    }

    @Test
    @DisplayName("Rule 9: MIN_RETRY_INTERVAL modifies retry scheduled earlier than 6h cooldown")
    void testMinRetryInterval() {
        Instant lastRetry = now.minus(Duration.ofHours(2));
        PolicyEngine.CustomerContext context = new PolicyEngine.CustomerContext(
                false, false, lastRetry, null, false, null, 0L, Set.of()
        );

        PolicyEngine.ProposedAction proposal = new PolicyEngine.ProposedAction(
                ActionType.SCHEDULE_RETRY,
                now.plus(Duration.ofHours(1)), // only 3h after last retry
                200L,
                null,
                null,
                "Quick retry",
                "UPI"
        );

        PolicyEngine.EvaluationResult result = policyEngine.evaluate(proposal, testCase, context, now);
        assertEquals(VerdictType.MODIFY, result.verdict());
        assertEquals("MIN_RETRY_INTERVAL", result.violatedRule());
        assertEquals(lastRetry.plus(Duration.ofHours(6)), result.finalAction().scheduledFor());
    }

    @Test
    @DisplayName("Rule 10: CONSENT_CHECK denies message if customer opted out")
    void testConsentCheck() {
        PolicyEngine.CustomerContext context = new PolicyEngine.CustomerContext(
                true, false, null, null, false, null, 0L, Set.of()
        );

        PolicyEngine.ProposedAction proposal = new PolicyEngine.ProposedAction(
                ActionType.SEND_MESSAGE,
                now.plus(Duration.ofHours(1)),
                35L,
                "WHATSAPP",
                "Please pay",
                "Nudge",
                "UPI"
        );

        PolicyEngine.EvaluationResult result = policyEngine.evaluate(proposal, testCase, context, now);
        assertEquals(VerdictType.DENY, result.verdict());
        assertEquals("CONSENT_CHECK", result.violatedRule());
    }

    @Test
    @DisplayName("Rule 11: MAX_CONTACTS denies message if 3 contacts already sent")
    void testMaxContacts() {
        testCase.setContactCount(3);
        PolicyEngine.ProposedAction proposal = new PolicyEngine.ProposedAction(
                ActionType.SEND_MESSAGE,
                now.plus(Duration.ofHours(1)),
                35L,
                "SMS",
                "Please pay",
                "Nudge",
                "UPI"
        );

        PolicyEngine.EvaluationResult result = policyEngine.evaluate(proposal, testCase, emptyContext(), now);
        assertEquals(VerdictType.DENY, result.verdict());
        assertEquals("MAX_CONTACTS", result.violatedRule());
    }

    @Test
    @DisplayName("Rule 13: QUIET_HOURS modifies messages scheduled at night (21:00-09:00 IST) to 09:00 IST")
    void testQuietHoursModification() {
        // 23:00 IST
        ZonedDateTime nightIst = ZonedDateTime.of(2026, 8, 20, 23, 0, 0, 0, ZoneId.of("Asia/Kolkata"));
        Instant nightInstant = nightIst.toInstant();

        PolicyEngine.ProposedAction proposal = new PolicyEngine.ProposedAction(
                ActionType.SEND_MESSAGE,
                nightInstant,
                35L,
                "WHATSAPP",
                "Payment link",
                "Night nudge",
                "UPI"
        );

        PolicyEngine.EvaluationResult result = policyEngine.evaluate(proposal, testCase, emptyContext(), now);
        assertEquals(VerdictType.MODIFY, result.verdict());
        assertEquals("QUIET_HOURS", result.violatedRule());

        ZonedDateTime modifiedIst = result.finalAction().scheduledFor().atZone(ZoneId.of("Asia/Kolkata"));
        assertEquals(9, modifiedIst.getHour());
        assertEquals(0, modifiedIst.getMinute());
    }
}
