package dev.reclaim.eval.arms;

import dev.reclaim.replay.ScenarioModel;

public class B0DoNothingArm implements EvaluationArm {
    @Override
    public String getArmName() {
        return "B0 — Do Nothing (Floor)";
    }

    @Override
    public ArmResult evaluateCase(ScenarioModel scenario) {
        // Zero interventions -> 0 rupees recovered, 0 cost
        return new ArmResult(false, 0L, 0L, 0, 0, false, false, true, false);
    }
}
