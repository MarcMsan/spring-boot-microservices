package com.vidopos.order.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.demo")
public class DemoProperties {

    private boolean failBeforeStatusUpdate;

    public boolean isFailBeforeStatusUpdate() {
        return failBeforeStatusUpdate;
    }

    public void setFailBeforeStatusUpdate(boolean failBeforeStatusUpdate) {
        this.failBeforeStatusUpdate = failBeforeStatusUpdate;
    }
}
