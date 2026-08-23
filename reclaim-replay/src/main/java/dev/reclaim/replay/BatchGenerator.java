package dev.reclaim.replay;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.File;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

public class BatchGenerator {

    private static final long SEED = 42L;

    public static List<ScenarioModel> generate300Batch() {
        Random random = new Random(SEED);
        List<ScenarioModel> list = new ArrayList<>();
        Instant now = Instant.parse("2026-08-20T10:00:00Z");

        // Failure distribution specification (§8.1)
        Map<String, Integer> distribution = new LinkedHashMap<>();
        distribution.put("INSUFFICIENT_FUNDS", 102); // 34%
        distribution.put("BANK_DOWNTIME", 42);       // 14%
        distribution.put("CARD_EXPIRED", 48);        // 16%
        distribution.put("MANDATE_REVOKED", 27);     // 9%
        distribution.put("LIMIT_EXCEEDED", 24);      // 8%
        distribution.put("TECHNICAL_DECLINE", 33);   // 11%
        distribution.put("CUSTOMER_CHURNED", 24);    // 8%

        long[] realisticAmounts = {29900L, 49900L, 99900L, 149900L, 199900L, 499900L, 999900L};

        int index = 1;
        for (Map.Entry<String, Integer> entry : distribution.entrySet()) {
            String code = entry.getKey();
            int count = entry.getValue();

            for (int i = 0; i < count; i++) {
                UUID caseId = UUID.nameUUIDFromBytes(("reclaim_case_" + index).getBytes());
                String subId = String.format("sub_batch_%04d", index);
                String custId = String.format("cust_%04d", index);
                long amount = realisticAmounts[random.nextInt(realisticAmounts.length)];

                ScenarioModel.TrueRecoverability recoverability;
                int optimalActions;
                int tolerance;
                int downtimeHours = 0;
                Instant salaryCycle = now.plus(random.nextInt(3) + 1, ChronoUnit.DAYS);

                switch (code) {
                    case "INSUFFICIENT_FUNDS" -> {
                        recoverability = ScenarioModel.TrueRecoverability.RETRY_TIMED;
                        optimalActions = 2;
                        tolerance = 3;
                    }
                    case "BANK_DOWNTIME" -> {
                        recoverability = ScenarioModel.TrueRecoverability.RETRY_TIMED;
                        optimalActions = 1;
                        tolerance = 2;
                        downtimeHours = 4 + random.nextInt(4);
                    }
                    case "CARD_EXPIRED" -> {
                        recoverability = ScenarioModel.TrueRecoverability.LINK_ONLY;
                        optimalActions = 2;
                        tolerance = 2;
                    }
                    case "MANDATE_REVOKED" -> {
                        recoverability = ScenarioModel.TrueRecoverability.UNRECOVERABLE;
                        optimalActions = 0;
                        tolerance = 1;
                    }
                    case "LIMIT_EXCEEDED" -> {
                        recoverability = ScenarioModel.TrueRecoverability.RETRY_ANY;
                        optimalActions = 1;
                        tolerance = 3;
                    }
                    case "TECHNICAL_DECLINE" -> {
                        recoverability = ScenarioModel.TrueRecoverability.RETRY_ANY;
                        optimalActions = 1;
                        tolerance = 3;
                    }
                    case "CUSTOMER_CHURNED" -> {
                        recoverability = ScenarioModel.TrueRecoverability.UNRECOVERABLE;
                        optimalActions = 0;
                        tolerance = 0;
                    }
                    default -> {
                        recoverability = ScenarioModel.TrueRecoverability.RETRY_ANY;
                        optimalActions = 1;
                        tolerance = 2;
                    }
                }

                String reason = switch (code) {
                    case "INSUFFICIENT_FUNDS" -> "Debit card / account balance insufficient for recurring charge";
                    case "BANK_DOWNTIME" -> "Issuer bank node unavailable during debit window";
                    case "CARD_EXPIRED" -> "Debit mandate card expired";
                    case "MANDATE_REVOKED" -> "Auto-debit permission revoked by customer";
                    case "LIMIT_EXCEEDED" -> "Customer transaction amount or velocity limit reached";
                    case "TECHNICAL_DECLINE" -> "Issuer switch timed out / transient decline";
                    case "CUSTOMER_CHURNED" -> "Explicit merchant cancellation requested";
                    default -> "Decline encountered";
                };

                list.add(new ScenarioModel(
                        caseId,
                        subId,
                        custId,
                        amount,
                        code,
                        reason,
                        recoverability,
                        optimalActions,
                        tolerance,
                        downtimeHours,
                        salaryCycle
                ));

                index++;
            }
        }

        return list;
    }

    public static void main(String[] args) throws Exception {
        List<ScenarioModel> batch = generate300Batch();
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.enable(SerializationFeature.INDENT_OUTPUT);

        File dir = new File("datasets");
        if (!dir.exists()) dir.mkdirs();

        File file = new File(dir, "batch-300.json");
        mapper.writeValue(file, batch);
        System.out.println("Generated and committed " + batch.size() + " test cases to " + file.getAbsolutePath());
    }
}
