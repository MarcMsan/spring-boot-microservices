package com.vidopos.inventory.reservation;

import com.vidopos.inventory.config.DemoProperties;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ReserveDelay {

    private static final Logger log = LoggerFactory.getLogger(ReserveDelay.class);

    private final DemoProperties properties;

    public ReserveDelay(DemoProperties properties) {
        this.properties = properties;
    }

    public void awaitAfterCommit() {
        Duration delay = properties.getReserveDelay();
        if (delay == null || delay.isZero() || delay.isNegative()) {
            return;
        }
        log.info("Delaying reservation response by {} after the decision was committed", delay);
        try {
            Thread.sleep(delay);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while delaying the reservation response", exception);
        }
    }
}
