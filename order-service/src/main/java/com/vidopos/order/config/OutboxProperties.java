package com.vidopos.order.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.outbox")
public class OutboxProperties {

    private boolean enabled = true;
    private int batchSize = 10;
    private Duration processingLease = Duration.ofSeconds(30);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public Duration getProcessingLease() {
        return processingLease;
    }

    public void setProcessingLease(Duration processingLease) {
        this.processingLease = processingLease;
    }

    public int leaseSeconds() {
        return (int) Math.max(1, processingLease.toSeconds());
    }
}
