package dev.reclaim.executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.reclaim.audit.AuditLedger;
import dev.reclaim.domain.*;
import dev.reclaim.statemachine.StateMachine;
import dev.reclaim.razorpay.RazorpayClient;
import dev.reclaim.repository.*;
import dev.reclaim.repository.*;
import dev.reclaim.repository.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ExecutorResilienceTest {

    @Test
    @DisplayName("Failure Mode 10.3: Razorpay reconciliation sweep cancels retry if subscription already cancelled")
    void testReconciliationSweepCancelsInactiveSubscriptionRetry() {
        RazorpayClient razorpayClient = Mockito.mock(RazorpayClient.class);
        RecoveryActionRepository actionRepo = Mockito.mock(RecoveryActionRepository.class);
        RecoveryCaseRepository caseRepo = Mockito.mock(RecoveryCaseRepository.class);
        HumanTaskRepository humanTaskRepo = Mockito.mock(HumanTaskRepository.class);
        AuditLedger auditLedger = Mockito.mock(AuditLedger.class);
        ObjectMapper objectMapper = new ObjectMapper();

        StateMachine stateMachine = Mockito.mock(StateMachine.class);
        dev.reclaim.reconciler.TruthReconciler truthReconciler = new dev.reclaim.reconciler.TruthReconciler(
                razorpayClient, caseRepo, actionRepo, stateMachine, auditLedger
        );

        ActionExecutor executor = new ActionExecutor(
                razorpayClient, actionRepo, caseRepo, humanTaskRepo, truthReconciler, auditLedger, objectMapper
        );

        RecoveryCase testCase = new RecoveryCase(
                UUID.randomUUID(),
                "sub_inactive_01",
                "cust_01",
                "INV-1",
                49900L,
                "INR",
                CaseState.EXECUTING,
                "INSUFFICIENT_FUNDS",
                "Failed",
                Instant.now(),
                UUID.randomUUID()
        );

        RecoveryAction action = new RecoveryAction(
                UUID.randomUUID(),
                testCase.getId(),
                ActionType.SCHEDULE_RETRY,
                "idemp_key_123",
                Instant.now(),
                ActionStatus.PENDING,
                200L
        );

        // Razorpay reconciliation sweep reports subscription cancelled / inactive on Razorpay
        when(razorpayClient.reconcileSubscriptionStatus("sub_inactive_01")).thenReturn(false);

        executor.executeAction(action, testCase);

        // Assert: retry was cancelled without making an invalid charge call!
        assertEquals(ActionStatus.CANCELLED, action.getStatus());
        verify(razorpayClient, never()).retrySubscriptionCharge(any(), any());
        verify(actionRepo, times(1)).save(action);
    }
}
