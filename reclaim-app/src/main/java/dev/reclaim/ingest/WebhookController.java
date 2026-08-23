package dev.reclaim.ingest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.reclaim.domain.RawEvent;
import dev.reclaim.events.EventProcessor;
import dev.reclaim.events.EventPublisher;
import dev.reclaim.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/webhooks/razorpay")
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

    private final HmacValidator hmacValidator;
    private final RawEventRepository rawEventRepository;
    private final EventPublisher eventPublisher;
    private final EventProcessor eventProcessor;
    private final ObjectMapper objectMapper;

    public WebhookController(HmacValidator hmacValidator,
                             RawEventRepository rawEventRepository,
                             EventPublisher eventPublisher,
                             EventProcessor eventProcessor,
                             ObjectMapper objectMapper) {
        this.hmacValidator = hmacValidator;
        this.rawEventRepository = rawEventRepository;
        this.eventPublisher = eventPublisher;
        this.eventProcessor = eventProcessor;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> handleWebhook(
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature,
            @RequestBody String rawPayload) {

        long startTime = System.currentTimeMillis();

        if (signature == null || !hmacValidator.isValidSignature(rawPayload, signature)) {
            log.warn("Invalid HMAC signature received for webhook");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("status", "error", "message", "Invalid HMAC signature"));
        }

        try {
            JsonNode root = objectMapper.readTree(rawPayload);
            String eventType = root.path("event").asText("unknown");
            String eventId = root.path("event_id").isMissingNode() || root.path("event_id").asText().isBlank()
                    ? root.path("id").asText("evt_" + UUID.randomUUID())
                    : root.path("event_id").asText();

            // Idempotency check on razorpay_event_id
            if (rawEventRepository.existsByRazorpayEventId(eventId)) {
                log.info("Duplicate webhook event {} detected. Acknowledging with 200 OK (idempotent no-op).", eventId);
                return ResponseEntity.ok(Map.of("status", "success", "message", "Duplicate event acknowledged"));
            }

            // Persist raw event
            RawEvent rawEvent = new RawEvent(UUID.randomUUID(), eventId, eventType, signature, rawPayload, Instant.now());
            rawEventRepository.save(rawEvent);

            // Publish to Kafka & process directly
            eventPublisher.publishRawEvent(eventId, rawPayload);
            eventProcessor.processRawEvent(rawEvent);

            long elapsed = System.currentTimeMillis() - startTime;
            log.info("Webhook {} of type {} processed in {}ms", eventId, eventType, elapsed);

            return ResponseEntity.ok(Map.of("status", "success", "eventId", eventId, "elapsedMs", elapsed));
        } catch (Exception e) {
            log.error("Failed to process webhook event: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }
}
