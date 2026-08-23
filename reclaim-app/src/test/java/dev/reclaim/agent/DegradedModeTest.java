package dev.reclaim.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.reclaim.domain.CaseState;
import dev.reclaim.domain.RecoveryCase;
import dev.reclaim.rules.RulesRecoveryEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DegradedModeTest {

    @Test
    @DisplayName("Failure Mode 10.2: LLM outage triggers Degraded Mode and continues recovery via Rules Engine")
    void testDegradedModeFallbackOnLlmFailure() {
        RulesRecoveryEngine rulesEngine = new RulesRecoveryEngine();
        ObjectMapper objectMapper = new ObjectMapper();

        // GeminiAgentClient configured with invalid base URL to simulate LLM 503 outage
        GeminiAgentClient agentClient = new GeminiAgentClient(
                "real_looking_api_key",
                "gemini-2.5-flash",
                "http://unreachable-llm-host:8989",
                rulesEngine,
                objectMapper
        );

        RecoveryCase testCase = new RecoveryCase(
                UUID.randomUUID(),
                "sub_degraded_test",
                "cust_101",
                "INV-99",
                49900L,
                "INR",
                CaseState.DIAGNOSING,
                "CARD_EXPIRED",
                "Card expired",
                Instant.now(),
                UUID.randomUUID()
        );

        // Invoke fallback handler directly / via circuit breaker
        GeminiAgentClient.AgentResponse response = agentClient.fallbackToRulesEngine(
                testCase, "Attempts: 0", Instant.now(), new RuntimeException("503 Service Unavailable: Gemini API rate limited")
        );

        // Assertions
        assertNotNull(response);
        assertTrue(response.degradedMode(), "System must set degradedMode = true");
        assertTrue(response.diagnosis().contains("DEGRADED_MODE"), "Diagnosis records degraded mode fallback");
        assertFalse(response.plan().isEmpty(), "Recovery plan must be generated despite LLM outage");
        assertEquals(0, response.promptTokens(), "Token usage should be 0 during fallback");
    }
}
