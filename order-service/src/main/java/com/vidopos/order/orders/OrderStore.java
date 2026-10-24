package com.vidopos.order.orders;

import com.vidopos.order.outbox.OutboxMessage;
import com.vidopos.order.outbox.OutboxRepository;
import com.vidopos.order.web.NotFoundException;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderStore {

    private final CustomerOrderRepository orders;
    private final OutboxRepository outbox;
    private final Clock clock;

    public OrderStore(CustomerOrderRepository orders, OutboxRepository outbox, Clock clock) {
        this.orders = orders;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Transactional
    public CustomerOrder insertPending(
            String idempotencyKey,
            String requestHash,
            String customerEmail,
            String correlationId,
            CreateOrderRequest request) {
        CustomerOrder order = CustomerOrder.pending(
                idempotencyKey,
                requestHash,
                customerEmail,
                correlationId,
                clock.instant());
        for (CreateOrderRequest.Item item : request.items()) {
            order.addItem(item.productId(), item.quantity());
        }
        orders.saveAndFlush(order);
        outbox.save(OutboxMessage.pending(order.getId(), clock.instant()));
        return order;
    }

    @Transactional(readOnly = true)
    public CustomerOrder getById(UUID id) {
        return orders.findById(id).orElseThrow(() -> new NotFoundException("Order not found: " + id));
    }

    @Transactional(readOnly = true)
    public CustomerOrder getByIdempotencyKey(String idempotencyKey) {
        return orders.findByIdempotencyKey(idempotencyKey)
                .orElseThrow(() -> new NotFoundException("Order not found for idempotency key"));
    }

    @Transactional(readOnly = true)
    public OrderResponse response(UUID id) {
        return OrderMapper.toResponse(getById(id));
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> list() {
        return orders.findAllByOrderByCreatedAtDesc().stream().map(OrderMapper::toResponse).toList();
    }
}
