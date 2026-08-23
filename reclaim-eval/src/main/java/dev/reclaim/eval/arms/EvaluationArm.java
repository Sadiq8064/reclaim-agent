package dev.reclaim.eval.arms;

import dev.reclaim.replay.ScenarioModel;

public interface EvaluationArm {
    String getArmName();
    ArmResult evaluateCase(ScenarioModel scenario);

    record ArmResult(
            boolean recovered,
            long recoveredPaise,
            long costPaise,
            int retryAttempts,
            int contactCount,
            boolean customerChurned,
            boolean escalated,
            boolean abandoned,
            boolean wastedAction
    ) {
        public long netRecoveredPaise() {
            return recovered ? (recoveredPaise - costPaise) : -costPaise;
        }
    }
}
