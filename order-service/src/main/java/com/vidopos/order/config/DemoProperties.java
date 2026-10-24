package com.vidopos.order.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.demo")
public class DemoProperties {

    /**
     * Throws after inventory has accepted the reservation and before this service records it.
     * The durable state then matches a crash in that window. Leave this false outside demos.
     */
    private boolean failBeforeStatusUpdate;

    public boolean isFailBeforeStatusUpdate() {
        return failBeforeStatusUpdate;
    }

    public void setFailBeforeStatusUpdate(boolean failBeforeStatusUpdate) {
        this.failBeforeStatusUpdate = failBeforeStatusUpdate;
    }
}
