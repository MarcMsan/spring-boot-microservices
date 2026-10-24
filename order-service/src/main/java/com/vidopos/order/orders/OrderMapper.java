package com.vidopos.order.orders;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public final class OrderMapper {

    private OrderMapper() {
    }

    public static OrderResponse toResponse(CustomerOrder order) {
        List<OrderItemResponse> items = order.getItems().stream()
                .sorted(java.util.Comparator.comparing(OrderItem::getProductId))
                .map(OrderMapper::toItem)
                .toList();
        return new OrderResponse(
                order.getId(),
                order.getCustomerEmail(),
                order.getStatus(),
                order.getTotal() == null ? null : money(order.getTotal()),
                order.getRejectionReason(),
                order.getIdempotencyKey(),
                order.getCorrelationId(),
                items,
                order.getCreatedAt(),
                order.getUpdatedAt());
    }

    private static OrderItemResponse toItem(OrderItem item) {
        BigDecimal unitPrice = item.getUnitPrice() == null ? null : money(item.getUnitPrice());
        BigDecimal lineTotal = unitPrice == null
                ? null
                : unitPrice.multiply(BigDecimal.valueOf(item.getQuantity())).setScale(2, RoundingMode.HALF_UP);
        return new OrderItemResponse(item.getProductId(), item.getQuantity(), unitPrice, lineTotal);
    }

    public static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
