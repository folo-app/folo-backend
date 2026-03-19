package com.folo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.storage")
public record FileStorageProperties(
        String uploadRootDir
) {
}
