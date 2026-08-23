package dev.reclaim;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.reclaim.agent.GeminiAgentClient;
import dev.reclaim.audit.AuditLedger;
import dev.reclaim.domain.*;
import dev.reclaim.events.EventProcessor;
import dev.reclaim.executor.ActionExecutor;
import dev.reclaim.ingest.HmacValidator;
import dev.reclaim.ingest.WebhookController;
import dev.reclaim.policy.PolicyEngine;
import dev.reclaim.razorpay.RazorpayClient;
import dev.reclaim.repository.*;
import dev.reclaim.rules.RulesRecoveryEngine;
import dev.reclaim.statemachine.StateMachine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class ReclaimRealLoopE2ETest {

    private RawEventRepository rawEventRepo;
    private RecoveryCaseRepository caseRepo;
    private AgentDecisionRepository decisionRepo;
    private PolicyVerdictRepository verdictRepo;
    private RecoveryActionRepository actionRepo;
    private AuditEntryRepository auditRepo;
    private HumanTaskRepository humanTaskRepo;

    private AuditLedger auditLedger;
    private StateMachine stateMachine;
    private PolicyEngine policyEngine;
    private RulesRecoveryEngine rulesEngine;
    private GeminiAgentClient agentClient;
    private RazorpayClient razorpayClient;
    private ActionExecutor actionExecutor;
    private EventProcessor eventProcessor;
    private ObjectMapper objectMapper;

    private final Map<UUID, RecoveryCase> casesDb = new HashMap<>();
    private final List<AuditEntry> auditEntriesDb = new ArrayList<>();

    @BeforeEach
    void setUp() {
        rawEventRepo = Mockito.mock(RawEventRepository.class);
        caseRepo = Mockito.mock(RecoveryCaseRepository.class);
        decisionRepo = Mockito.mock(AgentDecisionRepository.class);
        verdictRepo = Mockito.mock(PolicyVerdictRepository.class);
        actionRepo = Mockito.mock(RecoveryActionRepository.class);
        auditRepo = Mockito.mock(AuditEntryRepository.class);
        humanTaskRepo = Mockito.mock(HumanTaskRepository.class);

        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        objectMapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        auditLedger = new AuditLedger(auditRepo, objectMapper);
        stateMachine = new StateMachine(caseRepo, auditLedger);
        policyEngine = new PolicyEngine(3, 6, 3, 24, 15, 1000000L, 10000000L);
        rulesEngine = new RulesRecoveryEngine();
        agentClient = new GeminiAgentClient("placeholder", "gemini-2.5-flash", "https://api.test", rulesEngine, objectMapper);
        razorpayClient = new RazorpayClient("rzp_test_placeholder", "secret_placeholder", "https://api.razorpay.com/v1", objectMapper);
        actionExecutor = new ActionExecutor(razorpayClient, actionRepo, caseRepo, humanTaskRepo, auditLedger, objectMapper);

        eventProcessor = new EventProcessor(
                caseRepo, decisionRepo, verdictRepo, actionRepo,
                stateMachine, agentClient, policyEngine, actionExecutor,
                auditLedger, objectMapper
        );

        when(caseRepo.save(any(RecoveryCase.class))).thenAnswer(i -> {
            RecoveryCase c = i.getArgument(0);
            casesDb.put(c.getId(), c);
            return c;
        });

        when(caseRepo.findBySubscriptionIdAndStateNotIn(any(), any())).thenAnswer(i -> {
            String subId = i.getArgument(0);
            return casesDb.values().stream()
                    .filter(c -> c.getSubscriptionId().equals(subId) && !c.getState().isTerminal())
                    .findFirst();
        });

        when(auditRepo.save(any(AuditEntry.class))).thenAnswer(i -> {
            AuditEntry e = i.getArgument(0);
            e.setSeq((long) (auditEntriesDb.size() + 1));
            auditEntriesDb.add(e);
            return e;
        });

        when(auditRepo.findTopByOrderBySeqDesc()).thenAnswer(i ->
                auditEntriesDb.isEmpty() ? Optional.empty() : Optional.of(auditEntriesDb.get(auditEntriesDb.size() - 1))
        );
        when(auditRepo.findAllByOrderBySeqAsc()).thenReturn(auditEntriesDb);
    }

    @Test
    @DisplayName("Section 2.4 & 13.1: Full Live Recovery Loop end-to-end test")
    void testFullEndToEndRecoveryLoop() throws Exception {
        String subId = "sub_live_real_001";
        String eventId = "evt_001";

        String failureJson = String.format("""
            {
              "event": "subscription.pending",
              "event_id": "%s",
              "payload": {
                "subscription": {
                  "entity": {
                    "id": "%s",
                    "customer_id": "cust_live_001"
                  }
                },
                "payment": {
                  "entity": {
                    "amount": 49900,
                    "error_code": "INSUFFICIENT_FUNDS",
                    "error_description": "Account balance low"
                  }
                }
              }
            }
            """, eventId, subId);

        RawEvent failRaw = new RawEvent(UUID.randomUUID(), eventId, "subscription.pending", "sig_dummy", failureJson, Instant.now());

        // Step 1: Process failure event
        eventProcessor.processRawEvent(failRaw);

        // Assert case created and transitioned through AT_RISK -> DIAGNOSING -> PLANNED -> EXECUTING -> WAITING
        assertEquals(1, casesDb.size());
        RecoveryCase recoveryCase = casesDb.values().iterator().next();
        assertEquals(CaseState.WAITING, recoveryCase.getState());
        assertEquals("INSUFFICIENT_FUNDS", recoveryCase.getFailureCode());
        assertEquals(49900L, recoveryCase.getAmountPaise());

        // Step 2: Payment captured event arrives via alternative link
        String successJson = String.format("""
            {
              "event": "payment.captured",
              "event_id": "evt_002",
              "payload": {
                "subscription": {
                  "entity": {
                    "id": "%s"
                  }
                },
                "payment": {
                  "entity": {
                    "amount": 49900,
                    "subscription_id": "%s",
                    "status": "captured"
                  }
                }
              }
            }
            """, subId, subId);

        RawEvent successRaw = new RawEvent(UUID.randomUUID(), "evt_002", "payment.captured", "sig_dummy", successJson, Instant.now());
        eventProcessor.processRawEvent(successRaw);

        // Step 3: Assert terminal RECOVERED state
        assertEquals(CaseState.RECOVERED, recoveryCase.getState());
        assertEquals(CaseOutcome.RECOVERED, recoveryCase.getOutcome());
        assertEquals(49900L, recoveryCase.getRecoveredPaise());
        assertNotNull(recoveryCase.getClosedAt());

        // Step 4: Verify Cryptographic Audit Trail integrity
        AuditLedger.AuditVerifyResponse verifyResult = auditLedger.verifyChain();
        assertTrue(verifyResult.valid(), "Audit trail must be 100% valid and verified");
        assertTrue(verifyResult.totalEntries() >= 4, "Must contain all lifecycle transition entries");
    }
}
