package com.vidopos.order.outbox;

public class StatusUpdateInterruptedException extends RuntimeException {

    public StatusUpdateInterruptedException(String message) {
        super(message);
    }
}
