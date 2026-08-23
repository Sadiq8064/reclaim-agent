package dev.reclaim.repository;

import dev.reclaim.domain.CaseState;
import dev.reclaim.domain.RecoveryCase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RecoveryCaseRepository extends JpaRepository<RecoveryCase, UUID> {
    Optional<RecoveryCase> findBySubscriptionIdAndStateNotIn(String subscriptionId, List<CaseState> terminalStates);
    List<RecoveryCase> findBySubscriptionId(String subscriptionId);
    List<RecoveryCase> findByCustomerId(String customerId);
    List<RecoveryCase> findByState(CaseState state);
    List<RecoveryCase> findByRunId(UUID runId);
}
