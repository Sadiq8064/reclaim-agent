package dev.reclaim.repository;

import dev.reclaim.domain.HumanTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface HumanTaskRepository extends JpaRepository<HumanTask, UUID> {
    List<HumanTask> findByCaseId(UUID caseId);
    List<HumanTask> findByResolvedAtIsNullOrderByCreatedAtDesc();
}
