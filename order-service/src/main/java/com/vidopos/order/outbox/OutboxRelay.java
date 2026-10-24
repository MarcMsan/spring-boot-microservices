package com.vidopos.order.outbox;

import com.vidopos.order.config.DemoProperties;
import com.vidopos.order.config.OutboxProperties;
import com.vidopos.order.inventory.InventoryClient;
import com.vidopos.order.inventory.ReservationResult;
import com.vidopos.order.inventory.RetryableReservationException;
import com.vidopos.order.inventory.TerminalReservationException;
import com.vidopos.order.orders.CustomerOrder;
import com.vidopos.order.orders.OrderCompleter;
import com.vidopos.order.orders.OrderStatus;
import com.vidopos.order.orders.OrderStore;
import com.vidopos.order.web.Correlation;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxProperties properties;
    private final OutboxClaimer claimer;
    private final OutboxRepository outbox;
    private final OrderStore orders;
    private final OrderCompleter completer;
    private final InventoryClient inventory;
    private final DemoProperties demoProperties;

    public OutboxRelay(
            OutboxProperties properties,
            OutboxClaimer claimer,
            OutboxRepository outbox,
            OrderStore orders,
            OrderCompleter completer,
            InventoryClient inventory,
            DemoProperties demoProperties) {
        this.properties = properties;
        this.claimer = claimer;
        this.outbox = outbox;
        this.orders = orders;
        this.completer = completer;
        this.inventory = inventory;
        this.demoProperties = demoProperties;
    }

    @Scheduled(fixedDelayString = "${app.outbox.poll-interval:2s}")
    public void poll() {
        if (!properties.isEnabled()) {
            return;
        }
        processDue();
    }

    public void processDue() {
        for (int i = 0; i < properties.getBatchSize(); i++) {
            var claimed = claimer.claimDue();
            if (claimed.isEmpty()) {
                return;
            }
            process(claimed.get(0));
        }
    }

    public void tryProcess(UUID orderId) {
        var claimed = claimer.claimOrder(orderId);
        if (claimed.isEmpty()) {
            return;
        }
        process(claimed.get(0));
    }

    private void process(UUID outboxId) {
        OutboxMessage message = outbox.findById(outboxId).orElseThrow();
        CustomerOrder order = orders.getById(message.getOrderId());
        String previous = MDC.get(Correlation.MDC_KEY);
        MDC.put(Correlation.MDC_KEY, order.getCorrelationId());
        try {
            if (order.getStatus() != OrderStatus.PENDING) {
                completer.markSent(outboxId);
                return;
            }
            ReservationResult result = inventory.reserve(order);
            if (demoProperties.isFailBeforeStatusUpdate()) {
                throw new StatusUpdateInterruptedException(
                        "Simulated stop after inventory reserved stock and before the order status was recorded");
            }
            completer.complete(order.getId(), outboxId, result);
            log.info(
                    "Order resolved orderId={} inventoryStatus={} correlationId={}",
                    order.getId(),
                    result.status(),
                    order.getCorrelationId());
        } catch (TerminalReservationException exception) {
            completer.complete(
                    order.getId(),
                    outboxId,
                    ReservationResult.rejected(order.getId(), exception.getMessage()));
            log.info(
                    "Order rejected from inventory response orderId={} correlationId={} reason={}",
                    order.getId(),
                    order.getCorrelationId(),
                    exception.getMessage());
        } catch (RetryableReservationException | StatusUpdateInterruptedException exception) {
            log.warn(
                    "Reservation attempt will be retried orderId={} correlationId={} reason={}",
                    order.getId(),
                    order.getCorrelationId(),
                    exception.getMessage());
            completer.reschedule(outboxId, exception.getMessage());
        } catch (RuntimeException exception) {
            log.error(
                    "Reservation attempt failed orderId={} correlationId={}",
                    order.getId(),
                    order.getCorrelationId(),
                    exception);
            completer.reschedule(outboxId, exception.getMessage());
        } finally {
            if (previous == null) {
                MDC.remove(Correlation.MDC_KEY);
            } else {
                MDC.put(Correlation.MDC_KEY, previous);
            }
        }
    }
}
