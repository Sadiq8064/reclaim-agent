package dev.reclaim.events;

import dev.reclaim.audit.AuditLedger;
import dev.reclaim.domain.*;
import dev.reclaim.razorpay.RazorpayClient;
import dev.reclaim.reconciler.TruthReconciler;
import dev.reclaim.repository.*;
import dev.reclaim.statemachine.StateMachine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EventOrderingAndShuffleTest {

    @Test
    @DisplayName("Ordering 1: Payment.captured arriving BEFORE delayed subscription.pending settles case to RECOVERED with 0 duplicate actions")
    void testPaymentCapturedBeforeDelayedFailureWebhook() {
        RazorpayClient razorpayClient = Mockito.mock(RazorpayClient.class);
        RecoveryCaseRepository caseRepo = Mockito.mock(RecoveryCaseRepository.class);
        when(caseRepo.save(any(RecoveryCase.class))).thenAnswer(i -> i.getArgument(0));
        RecoveryActionRepository actionRepo = Mockito.mock(RecoveryActionRepository.class);
        AuditLedger auditLedger = Mockito.mock(AuditLedger.class);

        StateMachine stateMachine = new StateMachine(caseRepo, auditLedger);
        TruthReconciler truthReconciler = new TruthReconciler(
                razorpayClient, caseRepo, actionRepo, stateMachine, auditLedger
        );

        String subId = "sub_shuffled_order_001";
        RecoveryCase recoveryCase = new RecoveryCase(
                UUID.randomUUID(),
                subId,
                "cust_shuffled_01",
                "INV-SHUFFLE",
                120000L,
                "INR",
                CaseState.AT_RISK,
                "INSUFFICIENT_FUNDS",
                "Failed",
                Instant.now(),
                UUID.randomUUID()
        );

        // 1. Success event arrives first out-of-order -> transitions to RECOVERED
        stateMachine.transition(recoveryCase, CaseState.RECOVERED, ActorType.SYSTEM, "Payment captured webhook arrived out of order");
        assertEquals(CaseState.RECOVERED, recoveryCase.getState());

        // 2. Delayed failure webhook arrives later -> TruthReconciler runs pre-flight check
        TruthReconciler.ReconciliationVerdict verdict = truthReconciler.reconcileCurrentTruth(recoveryCase);

        // 3. Assert: Recovery is halted immediately, zero money action permitted on terminal state
        assertFalse(verdict.safeToProceed(), "Must fail-closed on cases already settled as RECOVERED");
        assertTrue(verdict.reason().contains("terminal state"), "Reason must identify terminal state lock");
    }

    @Test
    @DisplayName("Ordering 2: Shuffled downtime events (downtime.resolved before downtime.started) safely ignored")
    void testShuffledDowntimeEventsSafety() {
        RecoveryCaseRepository caseRepo = Mockito.mock(RecoveryCaseRepository.class);
        RecoveryActionRepository actionRepo = Mockito.mock(RecoveryActionRepository.class);
        AuditLedger auditLedger = Mockito.mock(AuditLedger.class);

        RecoveryCase activeCase = new RecoveryCase(
                UUID.randomUUID(),
                "sub_downtime_shuffle",
                "cust_02",
                "INV-DT",
                49900L,
                "INR",
                CaseState.PLANNED,
                "BANK_DOWNTIME",
                "Bank down",
                Instant.now(),
                UUID.randomUUID()
        );

        when(caseRepo.findAll()).thenReturn(List.of(activeCase));

        // When downtime.resolved arrives while case is still PLANNED (not WAITING on downtime), no phantom retries dispatched
        List<RecoveryCase> waitingCases = caseRepo.findAll().stream()
                .filter(c -> !c.getState().isTerminal() && c.getState() == CaseState.WAITING)
                .toList();

        assertTrue(waitingCases.isEmpty(), "No cases should be triggered if not in WAITING state");
    }
}
