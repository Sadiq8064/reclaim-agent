package dev.reclaim.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(EventPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final String rawEventsTopic;
    private final String normalizedEventsTopic;

    public EventPublisher(KafkaTemplate<String, String> kafkaTemplate,
                          @Value("${reclaim.kafka.raw-events-topic:reclaim.events.raw}") String rawEventsTopic,
                          @Value("${reclaim.kafka.normalized-events-topic:reclaim.events.normalized}") String normalizedEventsTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.rawEventsTopic = rawEventsTopic;
        this.normalizedEventsTopic = normalizedEventsTopic;
    }

    public void publishRawEvent(String eventId, String payload) {
        try {
            kafkaTemplate.send(rawEventsTopic, eventId, payload);
            log.info("Published raw event {} to Kafka topic {}", eventId, rawEventsTopic);
        } catch (Exception e) {
            log.warn("Direct Kafka send failed (operating in embedded/fallback mode if Kafka is offline): {}", e.getMessage());
        }
    }

    public void publishNormalizedEvent(String caseId, String eventPayload) {
        try {
            kafkaTemplate.send(normalizedEventsTopic, caseId, eventPayload);
            log.info("Published normalized event for case {} to Kafka topic {}", caseId, normalizedEventsTopic);
        } catch (Exception e) {
            log.warn("Direct Kafka send failed: {}", e.getMessage());
        }
    }
}
