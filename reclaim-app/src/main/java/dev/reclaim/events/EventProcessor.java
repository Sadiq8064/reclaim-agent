package dev.reclaim.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.reclaim.agent.GeminiAgentClient;
import dev.reclaim.audit.AuditLedger;
import dev.reclaim.domain.*;
import dev.reclaim.executor.ActionExecutor;
import dev.reclaim.policy.PolicyEngine;
import dev.reclaim.repository.*;
import dev.reclaim.rules.RulesRecoveryEngine;
import dev.reclaim.statemachine.StateMachine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Service
public class EventProcessor {

    private static final Logger log = LoggerFactory.getLogger(EventProcessor.class);

    private final RecoveryCaseRepository recoveryCaseRepository;
    private final AgentDecisionRepository agentDecisionRepository;
    private final PolicyVerdictRepository policyVerdictRepository;
    private final RecoveryActionRepository recoveryActionRepository;
    private final StateMachine stateMachine;
    private final GeminiAgentClient agentClient;
    private final PolicyEngine policyEngine;
    private final ActionExecutor actionExecutor;
    private final AuditLedger auditLedger;
    private final ObjectMapper objectMapper;

    public EventProcessor(
            RecoveryCaseRepository recoveryCaseRepository,
            AgentDecisionRepository agentDecisionRepository,
            PolicyVerdictRepository policyVerdictRepository,
            RecoveryActionRepository recoveryActionRepository,
            StateMachine stateMachine,
            GeminiAgentClient agentClient,
            PolicyEngine policyEngine,
            ActionExecutor actionExecutor,
            AuditLedger auditLedger,
            ObjectMapper objectMapper) {
        this.recoveryCaseRepository = recoveryCaseRepository;
        this.agentDecisionRepository = agentDecisionRepository;
        this.policyVerdictRepository = policyVerdictRepository;
        this.recoveryActionRepository = recoveryActionRepository;
        this.stateMachine = stateMachine;
        this.agentClient = agentClient;
        this.policyEngine = policyEngine;
        this.actionExecutor = actionExecutor;
        this.auditLedger = auditLedger;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void processRawEvent(RawEvent rawEvent) {
        try {
            JsonNode root = objectMapper.readTree(rawEvent.getPayload());
            String eventType = rawEvent.getEventType();
            JsonNode payload = root.path("payload");

            log.info("Processing domain event: {} (id: {})", eventType, rawEvent.getRazorpayEventId());

            if ("subscription.pending".equalsIgnoreCase(eventType) || "payment.failed".equalsIgnoreCase(eventType)) {
                handleChargeFailure(rawEvent, root, payload);
            } else if ("payment.captured".equalsIgnoreCase(eventType) || "subscription.charged".equalsIgnoreCase(eventType)) {
                handlePaymentSuccess(rawEvent, root, payload);
            } else if ("subscription.cancelled".equalsIgnoreCase(eventType)) {
                handleSubscriptionCancelled(rawEvent, root, payload);
            } else if ("payments.downtime.started".equalsIgnoreCase(eventType)) {
                handleDowntimeStarted(rawEvent, root, payload);
            } else if ("payments.downtime.resolved".equalsIgnoreCase(eventType)) {
                handleDowntimeResolved(rawEvent, root, payload);
            } else {
                log.info("Event {} acknowledged without state change", eventType);
            }

            rawEvent.setProcessed(true);
        } catch (Exception e) {
            log.error("Failed to process event: {}", e.getMessage(), e);
            rawEvent.setProcessingAttempts(rawEvent.getProcessingAttempts() + 1);
            throw new RuntimeException(e);
        }
    }

    private void handleChargeFailure(RawEvent rawEvent, JsonNode root, JsonNode payload) throws Exception {
        JsonNode subNode = payload.path("subscription").path("entity");
        JsonNode paymentNode = payload.path("payment").path("entity");

        String subId = !subNode.path("id").isMissingNode() ? subNode.path("id").asText()
                : (paymentNode.path("subscription_id").isMissingNode() ? "sub_demo_" + UUID.randomUUID().toString().substring(0, 8) : paymentNode.path("subscription_id").asText());

        String customerId = !subNode.path("customer_id").isMissingNode() ? subNode.path("customer_id").asText()
                : (paymentNode.path("customer_id").isMissingNode() ? "cust_" + subId.substring(Math.max(0, subId.length() - 6)) : paymentNode.path("customer_id").asText());

        long amountPaise = !paymentNode.path("amount").isMissingNode() ? paymentNode.path("amount").asLong(49900L)
                : (subNode.path("plan").path("item").path("amount").isMissingNode() ? 49900L : subNode.path("plan").path("item").path("amount").asLong(49900L));

        String failureCode = !paymentNode.path("error_code").isMissingNode() ? paymentNode.path("error_code").asText("INSUFFICIENT_FUNDS")
                : (!root.path("failure_code").isMissingNode() ? root.path("failure_code").asText() : "INSUFFICIENT_FUNDS");

        String failureReason = !paymentNode.path("error_description").isMissingNode() ? paymentNode.path("error_description").asText("Charge authorization failed")
                : (!root.path("failure_reason").isMissingNode() ? root.path("failure_reason").asText() : "Recurring debit failed");

        UUID runId = root.path("run_id").isMissingNode() ? null : UUID.fromString(root.path("run_id").asText());

        // Find or create RecoveryCase
        List<CaseState> terminals = List.of(CaseState.RECOVERED, CaseState.ESCALATED, CaseState.ABANDONED);
        Optional<RecoveryCase> existingCaseOpt = recoveryCaseRepository.findBySubscriptionIdAndStateNotIn(subId, terminals);

        RecoveryCase recoveryCase;
        if (existingCaseOpt.isPresent()) {
            recoveryCase = existingCaseOpt.get();
            recoveryCase.setFailureCode(failureCode);
            recoveryCase.setFailureReasonRaw(failureReason);
            recoveryCase.incrementAttempts();
            log.info("Re-planning on existing active case {} for subscription {}", recoveryCase.getId(), subId);
        } else {
            recoveryCase = new RecoveryCase(
                    UUID.randomUUID(),
                    subId,
                    customerId,
                    "INV-" + UUID.randomUUID().toString().substring(0, 8),
                    amountPaise,
                    "INR",
                    CaseState.AT_RISK,
                    failureCode,
                    failureReason,
                    Instant.now(),
                    runId
            );
            recoveryCase = recoveryCaseRepository.save(recoveryCase);
            auditLedger.record(recoveryCase.getId(), runId, "CASE_OPENED", ActorType.SYSTEM,
                    Map.of("subscriptionId", subId, "amountPaise", amountPaise, "failureCode", failureCode));
        }

        // Trigger State Machine: AT_RISK -> DIAGNOSING
        stateMachine.transition(recoveryCase, CaseState.DIAGNOSING, ActorType.SYSTEM, "Charge failed: " + failureCode);

        // Invoke LLM Agent for Diagnosis & Plan
        String historySummary = "Attempts: " + recoveryCase.getAttemptCount() + ", Contacts: " + recoveryCase.getContactCount();
        GeminiAgentClient.AgentResponse agentResponse = agentClient.diagnoseAndPlan(recoveryCase, historySummary, Instant.now());

        // Persist Agent Decision
        AgentDecision decision = new AgentDecision(
                UUID.randomUUID(),
                recoveryCase.getId(),
                runId,
                rawEvent.getId(),
                agentResponse.degradedMode() ? "rules-engine-fallback" : "gemini-2.5-flash",
                agentResponse.promptTokens(),
                agentResponse.completionTokens(),
                agentResponse.diagnosis(),
                BigDecimal.valueOf(agentResponse.confidence()),
                agentResponse.reasoning(),
                objectMapper.writeValueAsString(agentResponse.plan()),
                objectMapper.writeValueAsString(Map.of("trajectory", "diagnose_and_propose_plan")),
                agentResponse.latencyMs(),
                agentResponse.degradedMode(),
                Instant.now()
        );
        agentDecisionRepository.save(decision);

        auditLedger.record(recoveryCase.getId(), runId, "AGENT_DECISION", ActorType.AGENT,
                Map.of("diagnosis", agentResponse.diagnosis(), "confidence", agentResponse.confidence(), "degradedMode", agentResponse.degradedMode()));

        // Evaluate each proposed action through deterministic Policy Engine
        PolicyEngine.CustomerContext customerContext = new PolicyEngine.CustomerContext(
                false,
                false,
                null,
                null,
                false,
                null,
                0L,
                Set.of()
        );

        List<RecoveryAction> approvedActions = new ArrayList<>();

        for (RulesRecoveryEngine.PlannedRuleAction step : agentResponse.plan()) {
            PolicyEngine.ProposedAction proposed = new PolicyEngine.ProposedAction(
                    step.actionType(),
                    step.scheduledFor(),
                    step.estimatedCostPaise(),
                    step.channel(),
                    step.message(),
                    step.reason(),
                    "UPI"
            );

            PolicyEngine.EvaluationResult evalResult = policyEngine.evaluate(proposed, recoveryCase, customerContext, Instant.now());

            // Save Policy Verdict
            PolicyVerdict verdict = new PolicyVerdict(
                    UUID.randomUUID(),
                    recoveryCase.getId(),
                    decision.getId(),
                    objectMapper.writeValueAsString(proposed),
                    evalResult.verdict(),
                    objectMapper.writeValueAsString(evalResult.passedRules()),
                    evalResult.violatedRule(),
                    objectMapper.writeValueAsString(evalResult.finalAction()),
                    Instant.now()
            );
            policyVerdictRepository.save(verdict);

            auditLedger.record(recoveryCase.getId(), runId, "POLICY_VERDICT", ActorType.POLICY,
                    Map.of("verdict", evalResult.verdict(), "violatedRule", evalResult.violatedRule() != null ? evalResult.violatedRule() : "none"));

            if (evalResult.verdict() == VerdictType.ALLOW || evalResult.verdict() == VerdictType.MODIFY) {
                PolicyEngine.ProposedAction finalAct = evalResult.finalAction();
                String idempotencyKey = "act_" + recoveryCase.getId() + "_" + finalAct.actionType() + "_" + approvedActions.size();

                RecoveryAction action = new RecoveryAction(
                        UUID.randomUUID(),
                        recoveryCase.getId(),
                        finalAct.actionType(),
                        idempotencyKey,
                        finalAct.scheduledFor(),
                        ActionStatus.PENDING,
                        finalAct.estimatedCostPaise()
                );
                recoveryActionRepository.save(action);
                approvedActions.add(action);
            }
        }

        // State Machine transition: DIAGNOSING -> PLANNED
        stateMachine.transition(recoveryCase, CaseState.PLANNED, ActorType.POLICY, "Recovery plan approved with " + approvedActions.size() + " actions");

        // Execute immediate actions
        if (!approvedActions.isEmpty()) {
            stateMachine.transition(recoveryCase, CaseState.EXECUTING, ActorType.EXECUTOR, "Executing initial recovery steps");
            for (RecoveryAction act : approvedActions) {
                // Execute actions due within 1 hour immediately
                if (act.getScheduledFor().isBefore(Instant.now().plusSeconds(3600))) {
                    actionExecutor.executeAction(act, recoveryCase);
                }
            }

            if (!recoveryCase.getState().isTerminal()) {
                stateMachine.transition(recoveryCase, CaseState.WAITING, ActorType.SYSTEM, "Awaiting customer/bank response or next scheduled window");
            }
        } else {
            stateMachine.transition(recoveryCase, CaseState.ABANDONED, ActorType.POLICY, "No viable recovery actions permitted under guardrails");
        }
    }

    private void handlePaymentSuccess(RawEvent rawEvent, JsonNode root, JsonNode payload) {
        JsonNode paymentNode = payload.path("payment").path("entity");
        JsonNode subNode = payload.path("subscription").path("entity");

        String subId = !subNode.path("id").isMissingNode() ? subNode.path("id").asText()
                : (paymentNode.path("subscription_id").isMissingNode() ? "" : paymentNode.path("subscription_id").asText());

        List<CaseState> terminals = List.of(CaseState.RECOVERED, CaseState.ESCALATED, CaseState.ABANDONED);
        Optional<RecoveryCase> caseOpt = recoveryCaseRepository.findBySubscriptionIdAndStateNotIn(subId, terminals);

        caseOpt.ifPresent(rc -> {
            log.info("Payment captured for subscription {}! Case {} closing as RECOVERED 💰", subId, rc.getId());

            // Cancel any pending scheduled actions
            List<RecoveryAction> pending = recoveryActionRepository.findByCaseIdAndStatus(rc.getId(), ActionStatus.PENDING);
            for (RecoveryAction pa : pending) {
                pa.setStatus(ActionStatus.CANCELLED);
                pa.setError("Payment captured successfully; cancelled subsequent actions.");
                recoveryActionRepository.save(pa);
            }

            stateMachine.transition(rc, CaseState.RECOVERED, ActorType.SYSTEM, "Payment successfully captured on Razorpay");
        });
    }

    private void handleSubscriptionCancelled(RawEvent rawEvent, JsonNode root, JsonNode payload) {
        JsonNode subNode = payload.path("subscription").path("entity");
        String subId = subNode.path("id").asText();

        List<CaseState> terminals = List.of(CaseState.RECOVERED, CaseState.ESCALATED, CaseState.ABANDONED);
        Optional<RecoveryCase> caseOpt = recoveryCaseRepository.findBySubscriptionIdAndStateNotIn(subId, terminals);

        caseOpt.ifPresent(rc -> {
            log.info("Subscription {} cancelled. Case {} transitioning to ABANDONED 🛑", subId, rc.getId());
            List<RecoveryAction> pending = recoveryActionRepository.findByCaseIdAndStatus(rc.getId(), ActionStatus.PENDING);
            for (RecoveryAction pa : pending) {
                pa.setStatus(ActionStatus.CANCELLED);
                recoveryActionRepository.save(pa);
            }
            stateMachine.transition(rc, CaseState.ABANDONED, ActorType.SYSTEM, "Subscription was cancelled by merchant/customer");
        });
    }

    private void handleDowntimeStarted(RawEvent rawEvent, JsonNode root, JsonNode payload) {
        log.info("Downtime started event received. Postponing imminent retries.");
        // Re-planning trigger
    }

    private void handleDowntimeResolved(RawEvent rawEvent, JsonNode root, JsonNode payload) {
        log.info("Downtime resolved event received. Re-evaluating postponed actions.");
        // Re-planning trigger
    }
}
