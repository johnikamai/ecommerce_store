package com.ecommerce.common.outbox;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.List;
public interface OutboxRepository extends JpaRepository<OutboxEvent,String> {
    List<OutboxEvent> findTop25ByDeliveredFalseAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(Instant now);
}
