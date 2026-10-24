package com.vidopos.order.web;

public class IdempotencyConflictException extends RuntimeException {

    public IdempotencyConflictException(String idempotencyKey) {
        super("Idempotency-Key was already used with a different request: " + idempotencyKey);
    }
}
