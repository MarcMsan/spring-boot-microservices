package com.vidopos.order.inventory;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ReservationResult(
        UUID orderId,
        String status,
        String reason,
        List<Item> items
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(UUID productId, int quantity, BigDecimal unitPrice) {
    }

    public boolean reserved() {
        return "RESERVED".equals(status);
    }

    public boolean rejected() {
        return "REJECTED".equals(status);
    }

    public static ReservationResult rejected(UUID orderId, String reason) {
        return new ReservationResult(orderId, "REJECTED", reason, List.of());
    }
}
