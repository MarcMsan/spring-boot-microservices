package com.vidopos.order.outbox;

import java.time.Duration;

public final class RetryBackoff {

    private RetryBackoff() {
    }

    public static Duration nextDelay(int attemptCount) {
        int exponent = Math.min(Math.max(attemptCount, 1), 5);
        long seconds = 1L << exponent;
        return Duration.ofSeconds(Math.min(seconds, 30));
    }
}
