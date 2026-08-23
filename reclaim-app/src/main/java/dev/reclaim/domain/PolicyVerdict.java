package dev.reclaim.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "policy_verdict")
public class PolicyVerdict {

    @Id
    private UUID id;

    @Column(name = "case_id", nullable = false)
    private UUID caseId;

    @Column(name = "decision_id")
    private UUID decisionId;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "proposed_action", nullable = false, columnDefinition = "jsonb")
    private String proposedAction;

    @Enumerated(EnumType.STRING)
    @Column(name = "verdict", nullable = false)
    private VerdictType verdict;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "rules_evaluated", columnDefinition = "jsonb")
    private String rulesEvaluated;

    @Column(name = "violated_rule")
    private String violatedRule;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "final_action", columnDefinition = "jsonb")
    private String finalAction;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public PolicyVerdict() {}

    public PolicyVerdict(UUID id, UUID caseId, UUID decisionId, String proposedAction,
                         VerdictType verdict, String rulesEvaluated, String violatedRule,
                         String finalAction, Instant createdAt) {
        this.id = id;
        this.caseId = caseId;
        this.decisionId = decisionId;
        this.proposedAction = proposedAction;
        this.verdict = verdict;
        this.rulesEvaluated = rulesEvaluated;
        this.violatedRule = violatedRule;
        this.finalAction = finalAction;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getCaseId() { return caseId; }
    public void setCaseId(UUID caseId) { this.caseId = caseId; }

    public UUID getDecisionId() { return decisionId; }
    public void setDecisionId(UUID decisionId) { this.decisionId = decisionId; }

    public String getProposedAction() { return proposedAction; }
    public void setProposedAction(String proposedAction) { this.proposedAction = proposedAction; }

    public VerdictType getVerdict() { return verdict; }
    public void setVerdict(VerdictType verdict) { this.verdict = verdict; }

    public String getRulesEvaluated() { return rulesEvaluated; }
    public void setRulesEvaluated(String rulesEvaluated) { this.rulesEvaluated = rulesEvaluated; }

    public String getViolatedRule() { return violatedRule; }
    public void setViolatedRule(String violatedRule) { this.violatedRule = violatedRule; }

    public String getFinalAction() { return finalAction; }
    public void setFinalAction(String finalAction) { this.finalAction = finalAction; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
