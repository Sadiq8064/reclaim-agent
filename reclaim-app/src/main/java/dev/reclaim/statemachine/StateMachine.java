package dev.reclaim.statemachine;

import dev.reclaim.audit.AuditLedger;
import dev.reclaim.domain.*;
import dev.reclaim.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

@Service
public class StateMachine {

    private static final Logger log = LoggerFactory.getLogger(StateMachine.class);

    private final RecoveryCaseRepository recoveryCaseRepository;
    private final AuditLedger auditLedger;

    private static final Map<CaseState, Set<CaseState>> LEGAL_TRANSITIONS = Map.of(
            CaseState.AT_RISK, Set.of(CaseState.DIAGNOSING, CaseState.ABANDONED, CaseState.RECOVERED),
            CaseState.DIAGNOSING, Set.of(CaseState.PLANNED, CaseState.ESCALATED, CaseState.ABANDONED, CaseState.RECOVERED),
            CaseState.PLANNED, Set.of(CaseState.EXECUTING, CaseState.ABANDONED, CaseState.RECOVERED),
            CaseState.EXECUTING, Set.of(CaseState.WAITING, CaseState.EXECUTING, CaseState.RECOVERED, CaseState.ESCALATED, CaseState.ABANDONED),
            CaseState.WAITING, Set.of(CaseState.DIAGNOSING, CaseState.EXECUTING, CaseState.RECOVERED, CaseState.ESCALATED, CaseState.ABANDONED),
            CaseState.RECOVERED, Set.of(),
            CaseState.ESCALATED, Set.of(),
            CaseState.ABANDONED, Set.of()
    );

    public StateMachine(RecoveryCaseRepository recoveryCaseRepository, AuditLedger auditLedger) {
        this.recoveryCaseRepository = recoveryCaseRepository;
        this.auditLedger = auditLedger;
    }

    @Transactional
    public RecoveryCase transition(RecoveryCase recoveryCase, CaseState targetState, ActorType actor, String reason) {
        CaseState currentState = recoveryCase.getState();

        if (currentState != null && currentState.isTerminal()) {
            throw new IllegalStateException("Cannot transition out of terminal state: " + currentState);
        }

        if (currentState != null && !LEGAL_TRANSITIONS.getOrDefault(currentState, Set.of()).contains(targetState)) {
            throw new IllegalStateException("Illegal state transition requested from " + currentState + " to " + targetState);
        }

        recoveryCase.setState(targetState);

        if (targetState.isTerminal()) {
            recoveryCase.setClosedAt(Instant.now());
            if (targetState == CaseState.RECOVERED) {
                recoveryCase.setOutcome(CaseOutcome.RECOVERED);
                recoveryCase.setRecoveredPaise(recoveryCase.getAmountPaise());
            } else if (targetState == CaseState.ESCALATED) {
                recoveryCase.setOutcome(CaseOutcome.ESCALATED);
            } else if (targetState == CaseState.ABANDONED) {
                recoveryCase.setOutcome(CaseOutcome.ABANDONED);
            }
        }

        RecoveryCase saved = recoveryCaseRepository.save(recoveryCase);

        auditLedger.record(saved.getId(), saved.getRunId(), "STATE_TRANSITION", actor,
                Map.of("from", currentState != null ? currentState.name() : "NEW",
                        "to", targetState.name(),
                        "reason", reason));

        log.info("Case {} transitioned {} -> {} by {} (reason: {})", saved.getId(), currentState, targetState, actor, reason);
        return saved;
    }
}
