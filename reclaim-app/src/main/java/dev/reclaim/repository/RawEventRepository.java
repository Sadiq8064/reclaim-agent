package dev.reclaim.repository;

import dev.reclaim.domain.RawEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RawEventRepository extends JpaRepository<RawEvent, UUID> {
    Optional<RawEvent> findByRazorpayEventId(String razorpayEventId);
    boolean existsByRazorpayEventId(String razorpayEventId);
    List<RawEvent> findByProcessedFalseOrderByReceivedAtAsc();
}
