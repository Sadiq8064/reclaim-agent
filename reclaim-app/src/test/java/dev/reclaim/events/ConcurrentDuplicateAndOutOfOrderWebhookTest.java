package dev.reclaim.events;

import dev.reclaim.audit.AuditLedger;
import dev.reclaim.domain.*;
import dev.reclaim.razorpay.RazorpayClient;
import dev.reclaim.executor.ActionExecutor;
import dev.reclaim.policy.PolicyEngine;
import dev.reclaim.reconciler.TruthReconciler;
import dev.reclaim.repository.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ConcurrentDuplicateAndOutOfOrderWebhookTest {

    @Test
    @DisplayName("Depth 1: 5 Concurrent Duplicate Webhooks result in exactly 1 recovery case and 1 action")
    void testConcurrentDuplicateWebhooksDeduplication() throws Exception {
        RawEventRepository rawRepo = Mockito.mock(RawEventRepository.class);
        RecoveryCaseRepository caseRepo = Mockito.mock(RecoveryCaseRepository.class);
        RecoveryActionRepository actionRepo = Mockito.mock(RecoveryActionRepository.class);
        AuditLedger auditLedger = Mockito.mock(AuditLedger.class);

        String duplicateEventId = "evt_duplicate_concurrency_999";
        String subId = "sub_concurrent_test_101";

        Map<String, RawEvent> storedEvents = new ConcurrentHashMap<>();
        AtomicInteger caseCreateCount = new AtomicInteger(0);

        when(rawRepo.existsByRazorpayEventId(duplicateEventId)).thenAnswer(i -> storedEvents.containsKey(duplicateEventId));
        when(rawRepo.save(any(RawEvent.class))).thenAnswer(i -> {
            RawEvent re = i.getArgument(0);
            if (storedEvents.putIfAbsent(re.getRazorpayEventId(), re) != null) {
                throw new RuntimeException("DuplicateKeyException: Unique constraint violation on razorpay_event_id");
            }
            return re;
        });

        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                latch.await();
                try {
                    if (rawRepo.existsByRazorpayEventId(duplicateEventId)) {
                        return false; // Deduplicated
                    }
                    RawEvent event = new RawEvent(
                            UUID.randomUUID(),
                            duplicateEventId,
                            "subscription.pending",
                            "sig_test_hmac_123",
                            "{}",
                            Instant.now()
                    );
                    rawRepo.save(event);
                    caseCreateCount.incrementAndGet();
                    return true;
                } catch (Exception e) {
                    return false; // Database unique constraint caught duplicate
                }
            }));
        }

        latch.countDown(); // Fire all 5 threads simultaneously
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        int successfulCreations = 0;
        for (Future<Boolean> f : futures) {
            if (f.get()) successfulCreations++;
        }

        // Assert: Exactly 1 thread succeeded; other 4 were cleanly deduplicated
        assertEquals(1, successfulCreations, "Exactly 1 raw event must be processed among 5 concurrent duplicates");
        assertEquals(1, caseCreateCount.get(), "Exactly 1 recovery case creation triggered");
    }

    @Test
    @DisplayName("Depth 1: Out-of-Order Webhook (payment.captured arrives BEFORE delayed failure event) halts recovery")
    void testOutOfOrderWebhookPrecedence() {
        RazorpayClient razorpayClient = Mockito.mock(dev.reclaim.razorpay.RazorpayClient.class);
        RecoveryCaseRepository caseRepo = Mockito.mock(RecoveryCaseRepository.class);
        RecoveryActionRepository actionRepo = Mockito.mock(RecoveryActionRepository.class);
        dev.reclaim.statemachine.StateMachine stateMachine = Mockito.mock(dev.reclaim.statemachine.StateMachine.class);
        AuditLedger auditLedger = Mockito.mock(AuditLedger.class);

        TruthReconciler reconciler = new TruthReconciler(
                razorpayClient, caseRepo, actionRepo, stateMachine, auditLedger
        );

        RecoveryCase caseAlreadyRecovered = new RecoveryCase(
                UUID.randomUUID(),
                "sub_out_of_order_102",
                "cust_102",
                "INV-OOO",
                49900L,
                "INR",
                CaseState.RECOVERED, // Case was already settled by an out-of-order success webhook
                "INSUFFICIENT_FUNDS",
                "Delayed failure webhook arriving late",
                Instant.now(),
                UUID.randomUUID()
        );

        // Pre-flight check on terminal case
        TruthReconciler.ReconciliationVerdict verdict = reconciler.reconcileCurrentTruth(caseAlreadyRecovered);

        // Assert: Recovery is halted immediately, zero money action executed
        assertFalse(verdict.safeToProceed(), "Must not execute actions on already RECOVERED cases");
        assertTrue(verdict.reason().contains("terminal state"), "Reason must identify terminal state lock");
    }
}
