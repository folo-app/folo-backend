package com.folo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "security.email-verification")
public record EmailVerificationProperties(
        long ttlSeconds,
        long resendCooldownSeconds
) {
}
