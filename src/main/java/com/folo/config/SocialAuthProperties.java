package com.folo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.auth.social")
public record SocialAuthProperties(
        String appRedirectUrl,
        long authorizationStateTtlSeconds,
        long handoffCodeTtlSeconds,
        long pendingFlowTtlSeconds
) {
}
