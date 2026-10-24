package com.vidopos.order.orders;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerOrderRepository extends JpaRepository<CustomerOrder, UUID> {

    @Override
    @EntityGraph(attributePaths = "items")
    Optional<CustomerOrder> findById(UUID id);

    @EntityGraph(attributePaths = "items")
    Optional<CustomerOrder> findByIdempotencyKey(String idempotencyKey);

    @EntityGraph(attributePaths = "items")
    List<CustomerOrder> findAllByOrderByCreatedAtDesc();
}
