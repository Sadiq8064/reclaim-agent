package dev.reclaim.scheduler;

import dev.reclaim.domain.*;
import dev.reclaim.executor.ActionExecutor;
import dev.reclaim.repository.*;
import dev.reclaim.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class ActionScheduler {

    private static final Logger log = LoggerFactory.getLogger(ActionScheduler.class);

    private final RecoveryActionRepository recoveryActionRepository;
    private final RecoveryCaseRepository recoveryCaseRepository;
    private final ActionExecutor actionExecutor;

    public ActionScheduler(
            RecoveryActionRepository recoveryActionRepository,
            RecoveryCaseRepository recoveryCaseRepository,
            ActionExecutor actionExecutor) {
        this.recoveryActionRepository = recoveryActionRepository;
        this.recoveryCaseRepository = recoveryCaseRepository;
        this.actionExecutor = actionExecutor;
    }

    @Scheduled(fixedDelay = 5000)
    public void pollAndExecuteDueActions() {
        Instant now = Instant.now();
        List<RecoveryAction> dueActions = recoveryActionRepository.findByStatusAndScheduledForLessThanEqual(ActionStatus.PENDING, now);

        if (!dueActions.isEmpty()) {
            log.info("Scheduler discovered {} due recovery actions to execute", dueActions.size());
            for (RecoveryAction action : dueActions) {
                Optional<RecoveryCase> caseOpt = recoveryCaseRepository.findById(action.getCaseId());
                if (caseOpt.isPresent()) {
                    RecoveryCase rc = caseOpt.get();
                    if (rc.getState() != null && !rc.getState().isTerminal()) {
                        actionExecutor.executeAction(action, rc);
                    } else {
                        action.setStatus(ActionStatus.CANCELLED);
                        action.setError("Case in terminal state " + rc.getState());
                        recoveryActionRepository.save(action);
                    }
                }
            }
        }
    }
}
