package dev.reclaim.repository;

import dev.reclaim.domain.PolicyVerdict;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PolicyVerdictRepository extends JpaRepository<PolicyVerdict, UUID> {
    List<PolicyVerdict> findByCaseIdOrderByCreatedAtAsc(UUID caseId);
}
