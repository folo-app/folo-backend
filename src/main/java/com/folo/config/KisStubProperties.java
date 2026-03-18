package com.folo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.lang.Nullable;

@ConfigurationProperties(prefix = "integration.kis")
public record KisStubProperties(
        @Nullable String stubFile
) {
}
