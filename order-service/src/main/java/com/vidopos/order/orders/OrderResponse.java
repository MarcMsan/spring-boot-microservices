package com.vidopos.order.orders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        String customerEmail,
        OrderStatus status,
        BigDecimal total,
        String rejectionReason,
        String idempotencyKey,
        String correlationId,
        List<OrderItemResponse> items,
        Instant createdAt,
        Instant updatedAt
) {
}
