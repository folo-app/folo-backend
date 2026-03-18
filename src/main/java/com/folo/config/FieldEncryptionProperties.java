package com.folo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "security.field-encryption")
public record FieldEncryptionProperties(
        String secret
) {
}
