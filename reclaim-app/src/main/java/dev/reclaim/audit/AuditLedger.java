package dev.reclaim.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.reclaim.domain.ActorType;
import dev.reclaim.domain.AuditEntry;
import dev.reclaim.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuditLedger {

    private static final Logger log = LoggerFactory.getLogger(AuditLedger.class);
    public static final String GENESIS_HASH = "GENESIS_0000000000000000000000000000000000000000000000000000000000000000";

    private final AuditEntryRepository auditEntryRepository;
    private final ObjectMapper objectMapper;

    public AuditLedger(AuditEntryRepository auditEntryRepository, ObjectMapper objectMapper) {
        this.auditEntryRepository = auditEntryRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public synchronized AuditEntry record(UUID caseId, UUID runId, String entryType, ActorType actor, Object payload) {
        try {
            String rawJson = payload instanceof String ? (String) payload : objectMapper.writeValueAsString(payload);
            String canonicalJson = canonicalize(rawJson);

            Optional<AuditEntry> lastEntry = auditEntryRepository.findTopByOrderBySeqDesc();
            String prevHash = lastEntry.map(AuditEntry::getEntryHash).orElse(GENESIS_HASH);

            String entryHash = calculateHash(prevHash, canonicalJson);

            AuditEntry entry = new AuditEntry(caseId, runId, entryType, actor, canonicalJson, Instant.now(), prevHash, entryHash);
            AuditEntry saved = auditEntryRepository.save(entry);
            log.info("Audit entry #{} recorded [type={}, actor={}, hash={}]", saved.getSeq(), entryType, actor, entryHash);
            return saved;
        } catch (Exception e) {
            log.error("Failed to append to audit ledger: {}", e.getMessage(), e);
            throw new IllegalStateException("Audit ledger write failed", e);
        }
    }

    public AuditVerifyResponse verifyChain() {
        List<AuditEntry> entries = auditEntryRepository.findAllByOrderBySeqAsc();
        if (entries.isEmpty()) {
            return new AuditVerifyResponse(true, 0, null, "Audit ledger is empty (valid)");
        }

        String expectedPrevHash = GENESIS_HASH;
        for (AuditEntry entry : entries) {
            if (!expectedPrevHash.equals(entry.getPrevHash())) {
                return new AuditVerifyResponse(false, entries.size(), entry.getSeq(),
                        "Broken chain at seq " + entry.getSeq() + ": expected prevHash " + expectedPrevHash + " but found " + entry.getPrevHash());
            }

            String canonical = canonicalize(entry.getPayload());
            String calculatedHash = calculateHash(entry.getPrevHash(), canonical);
            if (!calculatedHash.equals(entry.getEntryHash())) {
                return new AuditVerifyResponse(false, entries.size(), entry.getSeq(),
                        "Hash mismatch at seq " + entry.getSeq() + ": payload was tampered with");
            }
            expectedPrevHash = entry.getEntryHash();
        }

        return new AuditVerifyResponse(true, entries.size(), null, "Audit chain integrity 100% verified across " + entries.size() + " entries.");
    }

    public String canonicalize(String json) {
        try {
            com.fasterxml.jackson.databind.JsonNode node = objectMapper.readTree(json);
            return toSortedCanonicalString(node);
        } catch (Exception e) {
            return json;
        }
    }

    private String toSortedCanonicalString(com.fasterxml.jackson.databind.JsonNode node) {
        if (node.isObject()) {
            com.fasterxml.jackson.databind.node.ObjectNode objectNode = (com.fasterxml.jackson.databind.node.ObjectNode) node;
            java.util.List<String> fieldNames = new java.util.ArrayList<>();
            objectNode.fieldNames().forEachRemaining(fieldNames::add);
            java.util.Collections.sort(fieldNames);

            StringBuilder sb = new StringBuilder("{");
            for (int i = 0; i < fieldNames.size(); i++) {
                String key = fieldNames.get(i);
                if (i > 0) sb.append(",");
                sb.append("\"").append(key).append("\":");
                sb.append(toSortedCanonicalString(objectNode.get(key)));
            }
            sb.append("}");
            return sb.toString();
        } else if (node.isArray()) {
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < node.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(toSortedCanonicalString(node.get(i)));
            }
            sb.append("]");
            return sb.toString();
        } else {
            return node.toString();
        }
    }

    public static String calculateHash(String prevHash, String canonicalPayload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String raw = prevHash + "::" + canonicalPayload;
            byte[] hashBytes = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(2 * hashBytes.length);
            for (byte b : hashBytes) {
                String hexByte = Integer.toHexString(0xFF & b);
                if (hexByte.length() == 1) hex.append('0');
                hex.append(hexByte);
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 calculation failed", e);
        }
    }

    public record AuditVerifyResponse(boolean valid, int totalEntries, Long brokenAtSeq, String message) {}
}
