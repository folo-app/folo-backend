package com.folo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ops")
public record AppOpsProperties(
        String triggerSecret
) {
}
