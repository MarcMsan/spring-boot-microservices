package com.vidopos.order.inventory;

import java.util.List;
import java.util.UUID;

public record ReserveStockRequest(UUID orderId, List<Item> items) {

    public record Item(UUID productId, int quantity) {
    }
}
