package com.folo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "security.jwt")
public record JwtProperties(
        String issuer,
        long accessTokenExpirationSeconds,
        long refreshTokenExpirationSeconds,
        String secret
) {
}
