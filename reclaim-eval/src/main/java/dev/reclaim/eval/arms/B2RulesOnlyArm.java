package dev.reclaim.eval.arms;

import dev.reclaim.replay.ScenarioModel;

public class B2RulesOnlyArm implements EvaluationArm {
    @Override
    public String getArmName() {
        return "B2 — Rules Heuristics (Deterministic No-LLM)";
    }

    @Override
    public ArmResult evaluateCase(ScenarioModel scenario) {
        boolean recovered = false;
        long costPaise = 0L;
        int retries = 0;
        int contacts = 0;
        boolean churned = false;
        boolean wasted = false;

        switch (scenario.failureCode()) {
            case "TECHNICAL_DECLINE" -> {
                // Static immediate retry works well on transient technical declines
                recovered = true;
                retries = 1;
                costPaise = 200L;
            }
            case "BANK_DOWNTIME" -> {
                // Static fixed 6h retry — misses cases where downtime exceeds 6h or clears early
                recovered = scenario.downtimeHours() <= 6;
                retries = 1;
                costPaise = 200L;
            }
            case "CARD_EXPIRED" -> {
                // Static generic payment link without channel preference or customer context
                recovered = (scenario.subscriptionId().hashCode() % 100) < 64;
                retries = 0;
                contacts = 1;
                costPaise = 35L;
            }
            case "INSUFFICIENT_FUNDS" -> {
                // Static fixed retry interval without salary-cycle correlation
                recovered = (scenario.subscriptionId().hashCode() % 100) < 66;
                retries = 1;
                contacts = 1;
                costPaise = 235L;
            }
            case "LIMIT_EXCEEDED" -> {
                // Static retry without split payment link
                recovered = (scenario.subscriptionId().hashCode() % 100) < 70;
                retries = 1;
                costPaise = 200L;
            }
            case "MANDATE_REVOKED" -> {
                // Static rule recognizes mandate revocation and stops
                recovered = false;
                costPaise = 0L;
                retries = 0;
                contacts = 0;
                wasted = false;
            }
            case "CUSTOMER_CHURNED" -> {
                // Static rule attempts 1 generic SMS before abandoning, hitting fatigue threshold
                recovered = false;
                contacts = 1;
                costPaise = 35L;
                churned = (scenario.customerToleranceThreshold() <= 1);
                wasted = true;
            }
        }

        long recoveredPaise = recovered ? scenario.amountPaise() : 0L;
        return new ArmResult(recovered, recoveredPaise, costPaise, retries, contacts, churned, false, !recovered, wasted);
    }
}
