package com.folo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "integration.kis")
public record KisStubProperties(
        String stubFile
) {
}
