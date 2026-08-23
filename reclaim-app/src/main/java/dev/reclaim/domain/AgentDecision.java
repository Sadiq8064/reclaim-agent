package dev.reclaim.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "agent_decision")
public class AgentDecision {

    @Id
    private UUID id;

    @Column(name = "case_id", nullable = false)
    private UUID caseId;

    @Column(name = "run_id")
    private UUID runId;

    @Column(name = "trigger_event_id")
    private UUID triggerEventId;

    @Column(name = "model")
    private String model;

    @Column(name = "prompt_tokens")
    private Integer promptTokens;

    @Column(name = "completion_tokens")
    private Integer completionTokens;

    @Column(name = "diagnosis", columnDefinition = "text")
    private String diagnosis;

    @Column(name = "confidence")
    private BigDecimal confidence;

    @Column(name = "reasoning", columnDefinition = "text")
    private String reasoning;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "proposed_plan", columnDefinition = "jsonb")
    private String proposedPlan;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "tool_calls", columnDefinition = "jsonb")
    private String toolCalls;

    @Column(name = "latency_ms")
    private Integer latencyMs;

    @Column(name = "degraded_mode", nullable = false)
    private boolean degradedMode = false;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public AgentDecision() {}

    public AgentDecision(UUID id, UUID caseId, UUID runId, UUID triggerEventId, String model,
                         Integer promptTokens, Integer completionTokens, String diagnosis,
                         BigDecimal confidence, String reasoning, String proposedPlan,
                         String toolCalls, Integer latencyMs, boolean degradedMode, Instant createdAt) {
        this.id = id;
        this.caseId = caseId;
        this.runId = runId;
        this.triggerEventId = triggerEventId;
        this.model = model;
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.diagnosis = diagnosis;
        this.confidence = confidence;
        this.reasoning = reasoning;
        this.proposedPlan = proposedPlan;
        this.toolCalls = toolCalls;
        this.latencyMs = latencyMs;
        this.degradedMode = degradedMode;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getCaseId() { return caseId; }
    public void setCaseId(UUID caseId) { this.caseId = caseId; }

    public UUID getRunId() { return runId; }
    public void setRunId(UUID runId) { this.runId = runId; }

    public UUID getTriggerEventId() { return triggerEventId; }
    public void setTriggerEventId(UUID triggerEventId) { this.triggerEventId = triggerEventId; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public Integer getPromptTokens() { return promptTokens; }
    public void setPromptTokens(Integer promptTokens) { this.promptTokens = promptTokens; }

    public Integer getCompletionTokens() { return completionTokens; }
    public void setCompletionTokens(Integer completionTokens) { this.completionTokens = completionTokens; }

    public String getDiagnosis() { return diagnosis; }
    public void setDiagnosis(String diagnosis) { this.diagnosis = diagnosis; }

    public BigDecimal getConfidence() { return confidence; }
    public void setConfidence(BigDecimal confidence) { this.confidence = confidence; }

    public String getReasoning() { return reasoning; }
    public void setReasoning(String reasoning) { this.reasoning = reasoning; }

    public String getProposedPlan() { return proposedPlan; }
    public void setProposedPlan(String proposedPlan) { this.proposedPlan = proposedPlan; }

    public String getToolCalls() { return toolCalls; }
    public void setToolCalls(String toolCalls) { this.toolCalls = toolCalls; }

    public Integer getLatencyMs() { return latencyMs; }
    public void setLatencyMs(Integer latencyMs) { this.latencyMs = latencyMs; }

    public boolean isDegradedMode() { return degradedMode; }
    public void setDegradedMode(boolean degradedMode) { this.degradedMode = degradedMode; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
