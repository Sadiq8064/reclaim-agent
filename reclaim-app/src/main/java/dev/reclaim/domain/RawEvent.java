package dev.reclaim.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "raw_event")
public class RawEvent {

    @Id
    private UUID id;

    @Column(name = "razorpay_event_id", unique = true, nullable = false)
    private String razorpayEventId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "signature", nullable = false)
    private String signature;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "processed", nullable = false)
    private boolean processed = false;

    @Column(name = "processing_attempts", nullable = false)
    private int processingAttempts = 0;

    public RawEvent() {}

    public RawEvent(UUID id, String razorpayEventId, String eventType, String signature, String payload, Instant receivedAt) {
        this.id = id;
        this.razorpayEventId = razorpayEventId;
        this.eventType = eventType;
        this.signature = signature;
        this.payload = payload;
        this.receivedAt = receivedAt != null ? receivedAt : Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getRazorpayEventId() { return razorpayEventId; }
    public void setRazorpayEventId(String razorpayEventId) { this.razorpayEventId = razorpayEventId; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getSignature() { return signature; }
    public void setSignature(String signature) { this.signature = signature; }

    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }

    public Instant getReceivedAt() { return receivedAt; }
    public void setReceivedAt(Instant receivedAt) { this.receivedAt = receivedAt; }

    public boolean isProcessed() { return processed; }
    public void setProcessed(boolean processed) { this.processed = processed; }

    public int getProcessingAttempts() { return processingAttempts; }
    public void setProcessingAttempts(int processingAttempts) { this.processingAttempts = processingAttempts; }
}
