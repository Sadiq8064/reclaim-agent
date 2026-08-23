package dev.reclaim.statemachine;

import dev.reclaim.audit.AuditLedger;
import dev.reclaim.domain.*;
import dev.reclaim.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class StateMachineTest {

    private RecoveryCaseRepository caseRepository;
    private AuditLedger auditLedger;
    private StateMachine stateMachine;
    private RecoveryCase testCase;

    @BeforeEach
    void setUp() {
        caseRepository = Mockito.mock(RecoveryCaseRepository.class);
        auditLedger = Mockito.mock(AuditLedger.class);
        stateMachine = new StateMachine(caseRepository, auditLedger);

        testCase = new RecoveryCase(
                UUID.randomUUID(),
                "sub_test_01",
                "cust_test_01",
                "INV-1",
                49900L,
                "INR",
                CaseState.AT_RISK,
                "INSUFFICIENT_FUNDS",
                "Declined",
                Instant.now(),
                UUID.randomUUID()
        );

        when(caseRepository.save(any(RecoveryCase.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    @DisplayName("Legal transition: AT_RISK -> DIAGNOSING -> PLANNED -> EXECUTING -> RECOVERED")
    void testFullHappyPathTransition() {
        stateMachine.transition(testCase, CaseState.DIAGNOSING, ActorType.SYSTEM, "Failure detected");
        assertEquals(CaseState.DIAGNOSING, testCase.getState());

        stateMachine.transition(testCase, CaseState.PLANNED, ActorType.POLICY, "Plan approved");
        assertEquals(CaseState.PLANNED, testCase.getState());

        stateMachine.transition(testCase, CaseState.EXECUTING, ActorType.EXECUTOR, "Actions in flight");
        assertEquals(CaseState.EXECUTING, testCase.getState());

        stateMachine.transition(testCase, CaseState.RECOVERED, ActorType.SYSTEM, "Payment captured");
        assertEquals(CaseState.RECOVERED, testCase.getState());
        assertEquals(CaseOutcome.RECOVERED, testCase.getOutcome());
        assertEquals(49900L, testCase.getRecoveredPaise());
        assertNotNull(testCase.getClosedAt());
    }

    @Test
    @DisplayName("Illegal transition: AT_RISK -> EXECUTING throws IllegalStateException")
    void testIllegalTransitionThrows() {
        assertThrows(IllegalStateException.class, () ->
                stateMachine.transition(testCase, CaseState.EXECUTING, ActorType.EXECUTOR, "Skipping diagnosis")
        );
    }

    @Test
    @DisplayName("Terminal absorbing state: Cannot transition out of RECOVERED")
    void testTerminalStateIsAbsorbing() {
        testCase.setState(CaseState.RECOVERED);
        assertThrows(IllegalStateException.class, () ->
                stateMachine.transition(testCase, CaseState.DIAGNOSING, ActorType.SYSTEM, "Reopening")
        );
    }
}
