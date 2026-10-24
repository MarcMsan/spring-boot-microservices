package com.vidopos.order.orders;

public record OrderSubmission(OrderResponse order, Outcome outcome) {

    public enum Outcome {
        CREATED_FINAL,
        CREATED_PENDING,
        REPLAYED
    }
}
