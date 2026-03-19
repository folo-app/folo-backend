package com.folo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.email")
public record AppEmailProperties(
        boolean smtpEnabled,
        String fromAddress,
        String fromName
) {
}
