package com.vidopos.order.outbox;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxRepository extends JpaRepository<OutboxMessage, UUID> {

    Optional<OutboxMessage> findByOrderId(UUID orderId);
}
