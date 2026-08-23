package dev.reclaim.replay;

import java.time.Instant;
import java.util.UUID;

public record ScenarioModel(
        UUID caseId,
        String subscriptionId,
        String customerId,
        long amountPaise,
        String failureCode,
        String failureReason,
        TrueRecoverability trueRecoverability,
        int optimalActionCount,
        int customerToleranceThreshold,
        int downtimeHours,
        Instant salaryCycleDate
) {
    public enum TrueRecoverability {
        RETRY_ANY,
        RETRY_TIMED,
        LINK_ONLY,
        UNRECOVERABLE
    }
}
