package com.vidopos.order.orders;

import com.vidopos.order.idempotency.RequestHasher;
import com.vidopos.order.outbox.OutboxRelay;
import com.vidopos.order.web.Correlation;
import com.vidopos.order.web.IdempotencyConflictException;
import com.vidopos.order.web.InvalidRequestException;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class OrderSubmissionService {

    private static final Pattern IDEMPOTENCY_KEY =
            Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._:-]{7,127}$");

    private final OrderStore orders;
    private final OutboxRelay relay;

    public OrderSubmissionService(OrderStore orders, OutboxRelay relay) {
        this.orders = orders;
        this.relay = relay;
    }

    public OrderSubmission submit(String idempotencyKey, CreateOrderRequest request) {
        String key = normalizeKey(idempotencyKey);
        rejectDuplicateProducts(request);
        String email = request.customerEmail().trim().toLowerCase(Locale.ROOT);
        String hash = RequestHasher.hash(request);
        boolean created;
        CustomerOrder order;
        try {
            order = orders.insertPending(key, hash, email, Correlation.current(), request);
            created = true;
        } catch (DataIntegrityViolationException exception) {
            if (!isIdempotencyConflict(exception)) {
                throw exception;
            }
            order = orders.getByIdempotencyKey(key);
            created = false;
            if (!order.getRequestHash().equals(hash)) {
                throw new IdempotencyConflictException(key);
            }
        }
        relay.tryProcess(order.getId());
        OrderResponse response = orders.response(order.getId());
        OrderSubmission.Outcome outcome;
        if (!created) {
            outcome = OrderSubmission.Outcome.REPLAYED;
        } else if (response.status() == OrderStatus.PENDING) {
            outcome = OrderSubmission.Outcome.CREATED_PENDING;
        } else {
            outcome = OrderSubmission.Outcome.CREATED_FINAL;
        }
        return new OrderSubmission(response, outcome);
    }

    public OrderResponse get(UUID id) {
        return orders.response(id);
    }

    public List<OrderResponse> list() {
        return orders.list();
    }

    private static String normalizeKey(String idempotencyKey) {
        if (idempotencyKey == null || !IDEMPOTENCY_KEY.matcher(idempotencyKey.trim()).matches()) {
            throw new InvalidRequestException(
                    "Idempotency-Key must be 8-128 characters and contain only letters, digits, '.', '_', ':', and '-'");
        }
        return idempotencyKey.trim();
    }

    private static void rejectDuplicateProducts(CreateOrderRequest request) {
        Set<UUID> seen = new HashSet<>();
        for (CreateOrderRequest.Item item : request.items()) {
            if (!seen.add(item.productId())) {
                throw new InvalidRequestException("Duplicate product in order: " + item.productId());
            }
        }
    }

    private static boolean isIdempotencyConflict(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.contains("uq_customer_orders_idempotency_key")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
