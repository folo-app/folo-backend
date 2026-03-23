package com.folo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "integration.opendart")
public record OpendartProperties(
        boolean enabled,
        String apiKey,
        String baseUrl
) {
}
