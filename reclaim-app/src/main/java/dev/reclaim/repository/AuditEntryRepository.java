package dev.reclaim.repository;

import dev.reclaim.domain.AuditEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AuditEntryRepository extends JpaRepository<AuditEntry, Long> {
    List<AuditEntry> findByCaseIdOrderBySeqAsc(UUID caseId);
    List<AuditEntry> findAllByOrderBySeqAsc();
    Optional<AuditEntry> findTopByOrderBySeqDesc();
}
