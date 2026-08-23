package dev.reclaim.repository;

import dev.reclaim.domain.AgentDecision;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AgentDecisionRepository extends JpaRepository<AgentDecision, UUID> {
    List<AgentDecision> findByCaseIdOrderByCreatedAtAsc(UUID caseId);
}
