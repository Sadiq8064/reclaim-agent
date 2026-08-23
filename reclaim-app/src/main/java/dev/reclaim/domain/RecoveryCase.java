package dev.reclaim.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "recovery_case")
public class RecoveryCase {

    @Id
    private UUID id;

    @Column(name = "subscription_id", nullable = false)
    private String subscriptionId;

    @Column(name = "customer_id", nullable = false)
    private String customerId;

    @Column(name = "merchant_ref")
    private String merchantRef;

    @Column(name = "amount_paise", nullable = false)
    private long amountPaise;

    @Column(name = "currency", nullable = false)
    private String currency = "INR";

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false)
    private CaseState state;

    @Column(name = "failure_code", nullable = false)
    private String failureCode;

    @Column(name = "failure_reason_raw")
    private String failureReasonRaw;

    @Column(name = "opened_at", nullable = false)
    private Instant openedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome")
    private CaseOutcome outcome;

    @Column(name = "recovered_paise", nullable = false)
    private long recoveredPaise = 0L;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount = 0;

    @Column(name = "contact_count", nullable = false)
    private int contactCount = 0;

    @Column(name = "cost_incurred_paise", nullable = false)
    private long costIncurredPaise = 0L;

    @Column(name = "run_id")
    private UUID runId;

    @Version
    @Column(name = "version", nullable = false)
    private long version = 0L;

    public RecoveryCase() {}

    public RecoveryCase(UUID id, String subscriptionId, String customerId, String merchantRef,
                        long amountPaise, String currency, CaseState state, String failureCode,
                        String failureReasonRaw, Instant openedAt, UUID runId) {
        this.id = id;
        this.subscriptionId = subscriptionId;
        this.customerId = customerId;
        this.merchantRef = merchantRef;
        this.amountPaise = amountPaise;
        this.currency = currency != null ? currency : "INR";
        this.state = state;
        this.failureCode = failureCode;
        this.failureReasonRaw = failureReasonRaw;
        this.openedAt = openedAt != null ? openedAt : Instant.now();
        this.runId = runId;
        this.version = 0L;
    }

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getSubscriptionId() { return subscriptionId; }
    public void setSubscriptionId(String subscriptionId) { this.subscriptionId = subscriptionId; }

    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }

    public String getMerchantRef() { return merchantRef; }
    public void setMerchantRef(String merchantRef) { this.merchantRef = merchantRef; }

    public long getAmountPaise() { return amountPaise; }
    public void setAmountPaise(long amountPaise) { this.amountPaise = amountPaise; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public CaseState getState() { return state; }
    public void setState(CaseState state) { this.state = state; }

    public String getFailureCode() { return failureCode; }
    public void setFailureCode(String failureCode) { this.failureCode = failureCode; }

    public String getFailureReasonRaw() { return failureReasonRaw; }
    public void setFailureReasonRaw(String failureReasonRaw) { this.failureReasonRaw = failureReasonRaw; }

    public Instant getOpenedAt() { return openedAt; }
    public void setOpenedAt(Instant openedAt) { this.openedAt = openedAt; }

    public Instant getClosedAt() { return closedAt; }
    public void setClosedAt(Instant closedAt) { this.closedAt = closedAt; }

    public CaseOutcome getOutcome() { return outcome; }
    public void setOutcome(CaseOutcome outcome) { this.outcome = outcome; }

    public long getRecoveredPaise() { return recoveredPaise; }
    public void setRecoveredPaise(long recoveredPaise) { this.recoveredPaise = recoveredPaise; }

    public int getAttemptCount() { return attemptCount; }
    public void setAttemptCount(int attemptCount) { this.attemptCount = attemptCount; }

    public int getContactCount() { return contactCount; }
    public void setContactCount(int contactCount) { this.contactCount = contactCount; }

    public long getCostIncurredPaise() { return costIncurredPaise; }
    public void setCostIncurredPaise(long costIncurredPaise) { this.costIncurredPaise = costIncurredPaise; }

    public UUID getRunId() { return runId; }
    public void setRunId(UUID runId) { this.runId = runId; }

    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }

    public void incrementAttempts() { this.attemptCount++; }
    public void incrementContacts() { this.contactCount++; }
    public void addCost(long costPaise) { this.costIncurredPaise += costPaise; }
}
