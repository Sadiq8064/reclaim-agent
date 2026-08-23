package dev.reclaim.eval;

import dev.reclaim.eval.arms.EvaluationArm;
import dev.reclaim.replay.ScenarioModel;

import java.util.*;

public class MetricsCalculator {

    public record ArmSummary(
            String armName,
            int totalCases,
            int recoveredCases,
            double overallRecoveryRate,
            double recoverableRecoveryRate,
            double grossRecoveredRupees,
            double totalCostRupees,
            double netRecoveredRupees,
            double actionsPerRecovery,
            int wastedActions,
            int churnTriggered,
            double ci95LowerRupees,
            double ci95UpperRupees,
            Map<String, SegmentStats> segmentStats
    ) {}

    public record SegmentStats(
            String failureCode,
            int total,
            int recovered,
            double recoveryRate,
            double netRupees
    ) {}

    public static ArmSummary calculate(EvaluationArm arm, List<ScenarioModel> batch) {
        int total = batch.size();
        int recovered = 0;
        long grossPaise = 0L;
        long costPaise = 0L;
        int totalActions = 0;
        int wasted = 0;
        int churn = 0;

        int recoverableTotal = 0;
        int recoverableRecovered = 0;

        Map<String, List<EvaluationArm.ArmResult>> bySegment = new HashMap<>();
        List<Double> perCaseNetRupees = new ArrayList<>();

        for (ScenarioModel s : batch) {
            EvaluationArm.ArmResult res = arm.evaluateCase(s);
            if (res.recovered()) {
                recovered++;
                grossPaise += res.recoveredPaise();
            }
            costPaise += res.costPaise();
            totalActions += (res.retryAttempts() + res.contactCount());
            if (res.wastedAction()) wasted += res.retryAttempts();
            if (res.customerChurned()) churn++;

            boolean isRecoverable = s.trueRecoverability() != ScenarioModel.TrueRecoverability.UNRECOVERABLE;
            if (isRecoverable) {
                recoverableTotal++;
                if (res.recovered()) recoverableRecovered++;
            }

            bySegment.computeIfAbsent(s.failureCode(), k -> new ArrayList<>()).add(res);
            perCaseNetRupees.add(res.netRecoveredPaise() / 100.0);
        }

        // Bootstrap 95% Confidence Interval
        double[] ci = bootstrap95CI(perCaseNetRupees, 1000);

        Map<String, SegmentStats> segments = new LinkedHashMap<>();
        for (Map.Entry<String, List<EvaluationArm.ArmResult>> entry : bySegment.entrySet()) {
            String code = entry.getKey();
            List<EvaluationArm.ArmResult> list = entry.getValue();
            int segRec = (int) list.stream().filter(EvaluationArm.ArmResult::recovered).count();
            double segNet = list.stream().mapToLong(EvaluationArm.ArmResult::netRecoveredPaise).sum() / 100.0;
            segments.put(code, new SegmentStats(code, list.size(), segRec, (segRec * 100.0 / list.size()), segNet));
        }

        double netRupees = (grossPaise - costPaise) / 100.0;

        return new ArmSummary(
                arm.getArmName(),
                total,
                recovered,
                (recovered * 100.0 / total),
                recoverableTotal == 0 ? 0.0 : (recoverableRecovered * 100.0 / recoverableTotal),
                grossPaise / 100.0,
                costPaise / 100.0,
                netRupees,
                recovered == 0 ? 0.0 : (double) totalActions / recovered,
                wasted,
                churn,
                ci[0],
                ci[1],
                segments
        );
    }

    private static double[] bootstrap95CI(List<Double> values, int iterations) {
        Random rand = new Random(42);
        int n = values.size();
        List<Double> sampleTotals = new ArrayList<>(iterations);

        for (int i = 0; i < iterations; i++) {
            double total = 0;
            for (int j = 0; j < n; j++) {
                total += values.get(rand.nextInt(n));
            }
            sampleTotals.add(total);
        }

        Collections.sort(sampleTotals);
        int lowerIdx = (int) (iterations * 0.025);
        int upperIdx = (int) (iterations * 0.975);

        return new double[]{sampleTotals.get(lowerIdx), sampleTotals.get(upperIdx)};
    }
}
