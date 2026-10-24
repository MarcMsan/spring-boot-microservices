package com.vidopos.inventory.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.demo")
public class DemoProperties {

    private Duration reserveDelay = Duration.ZERO;

    public Duration getReserveDelay() {
        return reserveDelay;
    }

    public void setReserveDelay(Duration reserveDelay) {
        this.reserveDelay = reserveDelay;
    }
}
