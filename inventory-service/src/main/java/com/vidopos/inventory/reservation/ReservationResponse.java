package com.vidopos.inventory.reservation;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ReservationResponse(
        UUID orderId,
        ReservationStatus status,
        String reason,
        List<Item> items
) {
    public record Item(UUID productId, int quantity, BigDecimal unitPrice) {
    }
}
