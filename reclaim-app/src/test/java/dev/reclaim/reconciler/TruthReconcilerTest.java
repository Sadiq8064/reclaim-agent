package dev.reclaim.reconciler;

import dev.reclaim.audit.AuditLedger;
import dev.reclaim.domain.*;
import dev.reclaim.razorpay.RazorpayClient;
import dev.reclaim.repository.RecoveryActionRepository;
import dev.reclaim.repository.RecoveryCaseRepository;
import dev.reclaim.statemachine.StateMachine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TruthReconcilerTest {

    @Test
    @DisplayName("Control Plane Tier 1: Truth Reconciler halts actions when subscription is inactive on Razorpay")
    void testTruthReconcilerHaltsInactiveSubscription() {
        RazorpayClient razorpayClient = Mockito.mock(RazorpayClient.class);
        RecoveryCaseRepository caseRepo = Mockito.mock(RecoveryCaseRepository.class);
        RecoveryActionRepository actionRepo = Mockito.mock(RecoveryActionRepository.class);
        StateMachine stateMachine = Mockito.mock(StateMachine.class);
        AuditLedger auditLedger = Mockito.mock(AuditLedger.class);

        TruthReconciler reconciler = new TruthReconciler(
                razorpayClient, caseRepo, actionRepo, stateMachine, auditLedger
        );

        RecoveryCase testCase = new RecoveryCase(
                UUID.randomUUID(),
                "sub_truth_inactive_99",
                "cust_truth_01",
                "INV-TRUTH-1",
                1200000L,
                "INR",
                CaseState.WAITING,
                "BANK_TEMPORARY_FAILURE",
                "Bank down",
                Instant.now(),
                UUID.randomUUID()
        );

        RecoveryAction pendingAction = new RecoveryAction(
                UUID.randomUUID(),
                testCase.getId(),
                ActionType.SCHEDULE_RETRY,
                "act_truth_test",
                Instant.now(),
                ActionStatus.PENDING,
                200L
        );

        when(razorpayClient.reconcileSubscriptionStatus("sub_truth_inactive_99")).thenReturn(false);
        when(actionRepo.findByCaseIdAndStatus(testCase.getId(), ActionStatus.PENDING)).thenReturn(List.of(pendingAction));

        TruthReconciler.ReconciliationVerdict verdict = reconciler.reconcileCurrentTruth(testCase);

        assertFalse(verdict.safeToProceed());
        assertEquals("SUBSCRIPTION_INACTIVE", verdict.currentTruthStatus());
        assertEquals(ActionStatus.CANCELLED, pendingAction.getStatus());
        verify(stateMachine, times(1)).transition(eq(testCase), eq(CaseState.ABANDONED), eq(ActorType.EXECUTOR), anyString());
    }

    @Test
    @DisplayName("Control Plane Tier 1: Truth Reconciler approves action when subscription is confirmed active")
    void testTruthReconcilerApprovesActiveSubscription() {
        RazorpayClient razorpayClient = Mockito.mock(RazorpayClient.class);
        RecoveryCaseRepository caseRepo = Mockito.mock(RecoveryCaseRepository.class);
        RecoveryActionRepository actionRepo = Mockito.mock(RecoveryActionRepository.class);
        StateMachine stateMachine = Mockito.mock(StateMachine.class);
        AuditLedger auditLedger = Mockito.mock(AuditLedger.class);

        TruthReconciler reconciler = new TruthReconciler(
                razorpayClient, caseRepo, actionRepo, stateMachine, auditLedger
        );

        RecoveryCase testCase = new RecoveryCase(
                UUID.randomUUID(),
                "sub_truth_active_100",
                "cust_truth_02",
                "INV-TRUTH-2",
                49900L,
                "INR",
                CaseState.DIAGNOSING,
                "INSUFFICIENT_FUNDS",
                "Low balance",
                Instant.now(),
                UUID.randomUUID()
        );

        when(razorpayClient.reconcileSubscriptionStatus("sub_truth_active_100")).thenReturn(true);

        TruthReconciler.ReconciliationVerdict verdict = reconciler.reconcileCurrentTruth(testCase);

        assertTrue(verdict.safeToProceed());
        assertEquals("ACTIVE_PENDING_RECOVERY", verdict.currentTruthStatus());
    }
}
