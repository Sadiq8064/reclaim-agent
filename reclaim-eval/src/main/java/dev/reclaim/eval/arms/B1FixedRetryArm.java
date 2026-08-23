package dev.reclaim.eval.arms;

import dev.reclaim.replay.ScenarioModel;

public class B1FixedRetryArm implements EvaluationArm {
    @Override
    public String getArmName() {
        return "B1 — Fixed Retries (Blind 3x)";
    }

    @Override
    public ArmResult evaluateCase(ScenarioModel scenario) {
        long retryCostPaise = 600L; // 3 retries * ₹2.00 = 600 paise
        boolean recovered = false;
        boolean wasted = false;
        boolean churned = false;

        switch (scenario.failureCode()) {
            case "TECHNICAL_DECLINE" -> {
                recovered = true;
                retryCostPaise = 200L; // succeeded on retry
            }
            case "INSUFFICIENT_FUNDS" -> {
                // Fixed schedule has ~42% chance of catching balance window
                recovered = (scenario.subscriptionId().hashCode() % 100) < 42;
                if (!recovered) churned = scenario.customerToleranceThreshold() <= 1;
            }
            case "LIMIT_EXCEEDED" -> {
                recovered = (scenario.subscriptionId().hashCode() % 100) < 55;
            }
            case "BANK_DOWNTIME" -> {
                // Fixed retry during active downtime wastes attempts
                recovered = (scenario.subscriptionId().hashCode() % 100) < 30;
                wasted = !recovered;
            }
            case "CARD_EXPIRED", "MANDATE_REVOKED", "CUSTOMER_CHURNED" -> {
                recovered = false;
                wasted = true; // Blind retries on unrecoverable mandates
                churned = true;
            }
        }

        long recoveredPaise = recovered ? scenario.amountPaise() : 0L;
        return new ArmResult(recovered, recoveredPaise, retryCostPaise, 3, 0, churned, false, !recovered, wasted);
    }
}
