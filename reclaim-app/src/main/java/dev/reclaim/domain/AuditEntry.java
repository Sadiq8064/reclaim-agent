package dev.reclaim.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_entry")
public class AuditEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long seq;

    @Column(name = "case_id", nullable = false)
    private UUID caseId;

    @Column(name = "run_id")
    private UUID runId;

    @Column(name = "entry_type", nullable = false)
    private String entryType;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor", nullable = false)
    private ActorType actor;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "prev_hash", nullable = false)
    private String prevHash;

    @Column(name = "entry_hash", nullable = false)
    private String entryHash;

    public AuditEntry() {}

    public AuditEntry(UUID caseId, UUID runId, String entryType, ActorType actor,
                      String payload, Instant createdAt, String prevHash, String entryHash) {
        this.caseId = caseId;
        this.runId = runId;
        this.entryType = entryType;
        this.actor = actor;
        this.payload = payload;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.prevHash = prevHash;
        this.entryHash = entryHash;
    }

    public Long getSeq() { return seq; }
    public void setSeq(Long seq) { this.seq = seq; }

    public UUID getCaseId() { return caseId; }
    public void setCaseId(UUID caseId) { this.caseId = caseId; }

    public UUID getRunId() { return runId; }
    public void setRunId(UUID runId) { this.runId = runId; }

    public String getEntryType() { return entryType; }
    public void setEntryType(String entryType) { this.entryType = entryType; }

    public ActorType getActor() { return actor; }
    public void setActor(ActorType actor) { this.actor = actor; }

    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public String getPrevHash() { return prevHash; }
    public void setPrevHash(String prevHash) { this.prevHash = prevHash; }

    public String getEntryHash() { return entryHash; }
    public void setEntryHash(String entryHash) { this.entryHash = entryHash; }
}
