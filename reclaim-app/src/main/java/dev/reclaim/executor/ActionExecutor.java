package dev.reclaim.executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.reclaim.audit.AuditLedger;
import dev.reclaim.domain.*;
import dev.reclaim.razorpay.RazorpayClient;
import dev.reclaim.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class ActionExecutor {

    private static final Logger log = LoggerFactory.getLogger(ActionExecutor.class);

    private final RazorpayClient razorpayClient;
    private final RecoveryActionRepository recoveryActionRepository;
    private final RecoveryCaseRepository recoveryCaseRepository;
    private final HumanTaskRepository humanTaskRepository;
    private final dev.reclaim.reconciler.TruthReconciler truthReconciler;
    private final AuditLedger auditLedger;
    private final ObjectMapper objectMapper;

    public ActionExecutor(
            RazorpayClient razorpayClient,
            RecoveryActionRepository recoveryActionRepository,
            RecoveryCaseRepository recoveryCaseRepository,
            HumanTaskRepository humanTaskRepository,
            dev.reclaim.reconciler.TruthReconciler truthReconciler,
            AuditLedger auditLedger,
            ObjectMapper objectMapper) {
        this.razorpayClient = razorpayClient;
        this.recoveryActionRepository = recoveryActionRepository;
        this.recoveryCaseRepository = recoveryCaseRepository;
        this.humanTaskRepository = humanTaskRepository;
        this.truthReconciler = truthReconciler;
        this.auditLedger = auditLedger;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void executeAction(RecoveryAction action, RecoveryCase recoveryCase) {
        log.info("Executing recovery action #{} [type={}, caseId={}]", action.getId(), action.getActionType(), recoveryCase.getId());

        try {
            // Pre-flight Recovery Truth Reconciliation
            if (action.getActionType() == ActionType.SCHEDULE_RETRY || action.getActionType() == ActionType.REQUEST_PAYMENT_METHOD_UPDATE || action.getActionType() == ActionType.CREATE_PAYMENT_LINK) {
                dev.reclaim.reconciler.TruthReconciler.ReconciliationVerdict verdict = truthReconciler.reconcileCurrentTruth(recoveryCase);
                if (!verdict.safeToProceed()) {
                    action.setStatus(ActionStatus.CANCELLED);
                    action.setError(verdict.reason());
                    recoveryActionRepository.save(action);
                    return;
                }
            }

            switch (action.getActionType()) {
                case SCHEDULE_RETRY -> {
                    RazorpayClient.RazorpayChargeRetryResponse response = razorpayClient.retrySubscriptionCharge(
                            recoveryCase.getSubscriptionId(),
                            action.getIdempotencyKey()
                    );
                    action.setStatus(ActionStatus.SUCCEEDED);
                    action.setExecutedAt(Instant.now());
                    action.setRazorpayRef(response.invoiceId());
                    action.setResponse(objectMapper.writeValueAsString(response));
                    recoveryCase.incrementAttempts();
                    recoveryCase.addCost(action.getCostPaise());
                }
                case CREATE_PAYMENT_LINK -> {
                    RazorpayClient.RazorpayPaymentLinkResponse response = razorpayClient.createPaymentLink(
                            recoveryCase.getAmountPaise(),
                            "Customer " + recoveryCase.getCustomerId(),
                            "+919876543210",
                            "customer_" + recoveryCase.getCustomerId() + "@example.com",
                            "Subscription Recovery Payment for " + recoveryCase.getSubscriptionId(),
                            action.getIdempotencyKey()
                    );
                    action.setStatus(ActionStatus.SUCCEEDED);
                    action.setExecutedAt(Instant.now());
                    action.setRazorpayRef(response.id());
                    action.setResponse(objectMapper.writeValueAsString(response));
                    recoveryCase.addCost(action.getCostPaise());
                }
                case REQUEST_PAYMENT_METHOD_UPDATE -> {
                    RazorpayClient.RazorpayPaymentLinkResponse response = razorpayClient.createPaymentLink(
                            recoveryCase.getAmountPaise(),
                            "Customer " + recoveryCase.getCustomerId(),
                            "+919876543210",
                            "customer_" + recoveryCase.getCustomerId() + "@example.com",
                            "Update Payment Method for Subscription " + recoveryCase.getSubscriptionId(),
                            action.getIdempotencyKey()
                    );
                    action.setStatus(ActionStatus.SUCCEEDED);
                    action.setExecutedAt(Instant.now());
                    action.setRazorpayRef(response.id());
                    action.setResponse(objectMapper.writeValueAsString(Map.of(
                            "workflow", "SUBSCRIPTION_PAYMENT_METHOD_UPDATE",
                            "paymentLinkId", response.id(),
                            "shortUrl", response.shortUrl(),
                            "status", "UPDATE_REQUESTED"
                    )));
                    recoveryCase.addCost(action.getCostPaise());
                }
                case SEND_CUSTOMER_NUDGE, SEND_MESSAGE -> {
                    action.setStatus(ActionStatus.SUCCEEDED);
                    action.setExecutedAt(Instant.now());
                    action.setResponse(objectMapper.writeValueAsString(Map.of(
                            "delivery", "DELIVERED",
                            "channel", "CONTEXT_AWARE_NUDGE",
                            "status", "DISPATCHED"
                    )));
                    recoveryCase.incrementContacts();
                    recoveryCase.addCost(action.getCostPaise());
                }
                case ESCALATE -> {
                    HumanTask task = new HumanTask(
                            UUID.randomUUID(),
                            recoveryCase.getId(),
                            "Escalated by policy or agent: high value or requires customer outreach review",
                            "HIGH",
                            objectMapper.writeValueAsString(Map.of("amountPaise", recoveryCase.getAmountPaise(), "failureCode", recoveryCase.getFailureCode())),
                            Instant.now()
                    );
                    humanTaskRepository.save(task);
                    action.setStatus(ActionStatus.SUCCEEDED);
                    action.setExecutedAt(Instant.now());
                    action.setRazorpayRef(task.getId().toString());
                    recoveryCase.setState(CaseState.ESCALATED);
                    recoveryCase.setOutcome(CaseOutcome.ESCALATED);
                    recoveryCase.setClosedAt(Instant.now());
                    recoveryCase.addCost(action.getCostPaise());
                }
                case CLOSE_CASE -> {
                    action.setStatus(ActionStatus.SUCCEEDED);
                    action.setExecutedAt(Instant.now());
                    recoveryCase.setState(CaseState.ABANDONED);
                    recoveryCase.setOutcome(CaseOutcome.ABANDONED);
                    recoveryCase.setClosedAt(Instant.now());
                }
                case WAIT -> {
                    action.setStatus(ActionStatus.SUCCEEDED);
                    action.setExecutedAt(Instant.now());
                }
            }

            recoveryActionRepository.save(action);
            recoveryCaseRepository.save(recoveryCase);

            auditLedger.record(recoveryCase.getId(), recoveryCase.getRunId(), "ACTION_EXECUTED", ActorType.EXECUTOR,
                    Map.of(
                            "actionId", action.getId(),
                            "actionType", action.getActionType(),
                            "status", action.getStatus(),
                            "costPaise", action.getCostPaise(),
                            "razorpayRef", action.getRazorpayRef() != null ? action.getRazorpayRef() : "none"
                    ));

        } catch (Exception e) {
            log.error("Execution failed for action {}: {}", action.getId(), e.getMessage(), e);
            action.setStatus(ActionStatus.FAILED);
            action.setError(e.getMessage());
            recoveryActionRepository.save(action);

            auditLedger.record(recoveryCase.getId(), recoveryCase.getRunId(), "ACTION_FAILED", ActorType.EXECUTOR,
                    Map.of("actionId", action.getId(), "error", e.getMessage()));
        }
    }
}
