package dev.reclaim.ingest;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.reclaim.domain.RawEvent;
import dev.reclaim.events.EventProcessor;
import dev.reclaim.events.EventPublisher;
import dev.reclaim.repository.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DuplicateWebhookTest {

    @Test
    @DisplayName("Failure Mode 10.1: 5 Concurrent identical webhooks produce exactly ONE raw_event and ONE action")
    void testConcurrentDuplicateWebhooksProduceExactlyOnce() throws Exception {
        HmacValidator hmacValidator = Mockito.mock(HmacValidator.class);
        RawEventRepository rawEventRepository = Mockito.mock(RawEventRepository.class);
        EventPublisher eventPublisher = Mockito.mock(EventPublisher.class);
        EventProcessor eventProcessor = Mockito.mock(EventProcessor.class);
        ObjectMapper objectMapper = new ObjectMapper();

        when(hmacValidator.isValidSignature(any(), any())).thenReturn(true);

        // Atomic set to simulate DB unique constraint on razorpay_event_id
        ConcurrentHashMap<String, Boolean> existingEvents = new ConcurrentHashMap<>();
        when(rawEventRepository.existsByRazorpayEventId(any())).thenAnswer(invocation -> {
            String eventId = invocation.getArgument(0);
            return existingEvents.putIfAbsent(eventId, true) != null;
        });

        WebhookController controller = new WebhookController(
                hmacValidator, rawEventRepository, eventPublisher, eventProcessor, objectMapper
        );

        String duplicateEventId = "evt_duplicate_test_9999";
        String payload = "{\"event\":\"subscription.pending\",\"event_id\":\"" + duplicateEventId + "\",\"payload\":{}}";
        String signature = "sig_valid";

        int threads = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger success200Count = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    latch.await();
                    ResponseEntity<Map<String, Object>> response = controller.handleWebhook(signature, payload);
                    if (response.getStatusCode().is2xxSuccessful()) {
                        success200Count.incrementAndGet();
                    }
                } catch (Exception ignored) {}
            });
        }

        latch.countDown();
        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

        // All 5 requests get 200 OK (idempotent contract)
        assertEquals(5, success200Count.get());

        // Exactly ONE raw event persisted
        verify(rawEventRepository, times(1)).save(any(RawEvent.class));

        // Exactly ONE event sent downstream to event processor
        verify(eventProcessor, times(1)).processRawEvent(any(RawEvent.class));
    }

    private void assertTrue(boolean condition) {
        org.junit.jupiter.api.Assertions.assertTrue(condition);
    }
}
