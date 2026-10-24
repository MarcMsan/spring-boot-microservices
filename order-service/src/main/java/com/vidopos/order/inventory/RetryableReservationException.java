package com.vidopos.order.inventory;

public class RetryableReservationException extends RuntimeException {

    public RetryableReservationException(String message) {
        super(message);
    }

    public RetryableReservationException(String message, Throwable cause) {
        super(message, cause);
    }
}
