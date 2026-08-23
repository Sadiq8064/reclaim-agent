package dev.reclaim.repository;

import dev.reclaim.domain.ActionStatus;
import dev.reclaim.domain.RecoveryAction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RecoveryActionRepository extends JpaRepository<RecoveryAction, UUID> {
    Optional<RecoveryAction> findByIdempotencyKey(String idempotencyKey);
    List<RecoveryAction> findByCaseIdOrderByScheduledForAsc(UUID caseId);
    List<RecoveryAction> findByStatusAndScheduledForLessThanEqual(ActionStatus status, Instant cutoff);
    List<RecoveryAction> findByCaseIdAndStatus(UUID caseId, ActionStatus status);
}
