package dev.reclaim.reconciler;

import dev.reclaim.audit.AuditLedger;
import dev.reclaim.domain.*;
import dev.reclaim.razorpay.RazorpayClient;
import dev.reclaim.repository.RecoveryActionRepository;
import dev.reclaim.repository.RecoveryCaseRepository;
import dev.reclaim.statemachine.StateMachine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class TruthReconciler {

    private static final Logger log = LoggerFactory.getLogger(TruthReconciler.class);

    private final RazorpayClient razorpayClient;
    private final RecoveryCaseRepository recoveryCaseRepository;
    private final RecoveryActionRepository recoveryActionRepository;
    private final StateMachine stateMachine;
    private final AuditLedger auditLedger;

    public record ReconciliationVerdict(
            boolean safeToProceed,
            String currentTruthStatus,
            String reason
    ) {}

    public TruthReconciler(
            RazorpayClient razorpayClient,
            RecoveryCaseRepository recoveryCaseRepository,
            RecoveryActionRepository recoveryActionRepository,
            StateMachine stateMachine,
            AuditLedger auditLedger) {
        this.razorpayClient = razorpayClient;
        this.recoveryCaseRepository = recoveryCaseRepository;
        this.recoveryActionRepository = recoveryActionRepository;
        this.stateMachine = stateMachine;
        this.auditLedger = auditLedger;
    }

    @Transactional
    public ReconciliationVerdict reconcileCurrentTruth(RecoveryCase recoveryCase) {
        log.info("Reconciling current ground truth for case #{} [subId={}] before money action",
                recoveryCase.getId(), recoveryCase.getSubscriptionId());

        // 1. Check if case is already in a terminal state
        if (recoveryCase.getState().isTerminal()) {
            return new ReconciliationVerdict(false, recoveryCase.getState().name(),
                    "Case is already in terminal state " + recoveryCase.getState());
        }

        // 2. Poll Razorpay API for live subscription status (Truth Check)
        boolean isActive = razorpayClient.reconcileSubscriptionStatus(recoveryCase.getSubscriptionId());

        if (!isActive) {
            log.warn("Reconciliation detected subscription {} is INACTIVE in Razorpay! Cancelling pending actions.",
                    recoveryCase.getSubscriptionId());

            // Cancel any pending scheduled actions
            cancelPendingActions(recoveryCase, "Subscription inactive during truth reconciliation");

            // Close case as ABANDONED
            stateMachine.transition(recoveryCase, CaseState.ABANDONED, ActorType.EXECUTOR,
                    "Truth reconciler detected cancelled subscription in Razorpay");
            recoveryCase.setOutcome(CaseOutcome.ABANDONED);
            recoveryCase.setClosedAt(Instant.now());
            recoveryCaseRepository.save(recoveryCase);

            auditLedger.record(recoveryCase.getId(), recoveryCase.getRunId(), "TRUTH_RECONCILED_CANCELLED", ActorType.EXECUTOR,
                    Map.of("truthStatus", "INACTIVE_IN_RAZORPAY", "action", "ABANDONED_RECOVERY"));

            return new ReconciliationVerdict(false, "SUBSCRIPTION_INACTIVE",
                    "Subscription cancelled on Razorpay. Halted all actions.");
        }

        // 3. Truth check passed - safe to proceed
        auditLedger.record(recoveryCase.getId(), recoveryCase.getRunId(), "TRUTH_VERIFIED_PROCEED", ActorType.EXECUTOR,
                Map.of("truthStatus", "ACTIVE_PENDING_RECOVERY", "verifiedAt", Instant.now().toString()));

        return new ReconciliationVerdict(true, "ACTIVE_PENDING_RECOVERY",
                "Current truth verified active. Safe to execute recovery action.");
    }

    private void cancelPendingActions(RecoveryCase recoveryCase, String reason) {
        List<RecoveryAction> pendingActions = recoveryActionRepository.findByCaseIdAndStatus(
                recoveryCase.getId(), ActionStatus.PENDING);
        for (RecoveryAction action : pendingActions) {
            action.setStatus(ActionStatus.CANCELLED);
            action.setError(reason);
            recoveryActionRepository.save(action);
        }
    }
}
