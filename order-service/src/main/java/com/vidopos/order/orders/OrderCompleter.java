package com.vidopos.order.orders;

import com.vidopos.order.inventory.ReservationResult;
import com.vidopos.order.inventory.RetryableReservationException;
import com.vidopos.order.outbox.OutboxMessage;
import com.vidopos.order.outbox.OutboxRepository;
import com.vidopos.order.outbox.OutboxStatus;
import com.vidopos.order.outbox.RetryBackoff;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderCompleter {

    private final CustomerOrderRepository orders;
    private final OutboxRepository outbox;
    private final Clock clock;

    public OrderCompleter(CustomerOrderRepository orders, OutboxRepository outbox, Clock clock) {
        this.orders = orders;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Transactional
    public void complete(UUID orderId, UUID outboxId, ReservationResult result) {
        CustomerOrder order = orders.findById(orderId).orElseThrow();
        OutboxMessage message = outbox.findById(outboxId).orElseThrow();
        if (order.getStatus() == OrderStatus.PENDING) {
            if (result.reserved()) {
                confirm(order, result);
            } else if (result.rejected()) {
                copyKnownPrices(order, result);
                String reason = result.reason() == null || result.reason().isBlank()
                        ? "Rejected by inventory"
                        : result.reason();
                order.reject(reason, clock.instant());
            } else {
                throw new RetryableReservationException("Unexpected reservation status: " + result.status());
            }
        }
        message.markSent(clock.instant());
    }

    @Transactional
    public void markSent(UUID outboxId) {
        OutboxMessage message = outbox.findById(outboxId).orElseThrow();
        message.markSent(clock.instant());
    }

    @Transactional
    public void reschedule(UUID outboxId, String error) {
        OutboxMessage message = outbox.findById(outboxId).orElseThrow();
        if (message.getStatus() == OutboxStatus.SENT) {
            return;
        }
        message.reschedule(
                error,
                clock.instant().plus(RetryBackoff.nextDelay(message.getAttemptCount())),
                clock.instant());
    }

    private void confirm(CustomerOrder order, ReservationResult result) {
        Map<UUID, ReservationResult.Item> priced = result.items() == null
                ? Map.of()
                : result.items().stream().collect(Collectors.toMap(ReservationResult.Item::productId, item -> item));
        BigDecimal total = BigDecimal.ZERO;
        for (OrderItem item : order.getItems()) {
            ReservationResult.Item decision = priced.get(item.getProductId());
            if (decision == null || decision.unitPrice() == null || decision.quantity() != item.getQuantity()) {
                throw new RetryableReservationException("Inventory confirmation did not match the order lines");
            }
            BigDecimal unitPrice = OrderMapper.money(decision.unitPrice());
            item.setUnitPrice(unitPrice);
            total = total.add(unitPrice.multiply(BigDecimal.valueOf(item.getQuantity())));
        }
        order.confirm(total.setScale(2, RoundingMode.HALF_UP), clock.instant());
    }

    private void copyKnownPrices(CustomerOrder order, ReservationResult result) {
        if (result.items() == null) {
            return;
        }
        Map<UUID, BigDecimal> prices = result.items().stream()
                .filter(item -> item.unitPrice() != null)
                .collect(Collectors.toMap(ReservationResult.Item::productId, ReservationResult.Item::unitPrice));
        for (OrderItem item : order.getItems()) {
            BigDecimal price = prices.get(item.getProductId());
            if (price != null) {
                item.setUnitPrice(OrderMapper.money(price));
            }
        }
    }
}
