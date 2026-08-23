package dev.reclaim.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.reclaim.domain.ActorType;
import dev.reclaim.domain.AuditEntry;
import dev.reclaim.repository.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class AuditLedgerTest {

    @Test
    @DisplayName("Cryptographic hash chain verification and tamper detection")
    void testAuditHashChainingAndTamperDetection() {
        AuditEntryRepository repository = Mockito.mock(AuditEntryRepository.class);
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        mapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        AuditLedger ledger = new AuditLedger(repository, mapper);

        List<AuditEntry> entries = new ArrayList<>();
        when(repository.save(any(AuditEntry.class))).thenAnswer(i -> {
            AuditEntry e = i.getArgument(0);
            e.setSeq((long) (entries.size() + 1));
            entries.add(e);
            return e;
        });
        when(repository.findTopByOrderBySeqDesc()).thenAnswer(i -> entries.isEmpty() ? Optional.empty() : Optional.of(entries.get(entries.size() - 1)));
        when(repository.findAllByOrderBySeqAsc()).thenReturn(entries);

        UUID caseId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();

        // 1. Append Genesis Entry
        AuditEntry e1 = ledger.record(caseId, runId, "CASE_OPENED", ActorType.SYSTEM, Map.of("action", "opened"));
        assertEquals(AuditLedger.GENESIS_HASH, e1.getPrevHash());
        assertNotNull(e1.getEntryHash());

        // 2. Append Second Entry
        AuditEntry e2 = ledger.record(caseId, runId, "PLAN_APPROVED", ActorType.POLICY, Map.of("actions", 2));
        assertEquals(e1.getEntryHash(), e2.getPrevHash());

        // 3. Verify intact chain
        AuditLedger.AuditVerifyResponse verifyResult = ledger.verifyChain();
        assertTrue(verifyResult.valid());
        assertEquals(2, verifyResult.totalEntries());
        assertNull(verifyResult.brokenAtSeq());

        // 4. Simulate tampering with entry payload
        e1.setPayload("{\"action\":\"tampered_payload\"}");
        AuditLedger.AuditVerifyResponse tamperedResult = ledger.verifyChain();
        assertFalse(tamperedResult.valid());
        assertEquals(1L, tamperedResult.brokenAtSeq());
    }
}
