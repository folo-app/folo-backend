package com.folo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app.auth.social.apple")
public record AppleSocialAuthProperties(
        boolean enabled,
        String issuer,
        String jwkSetUrl,
        List<String> audiences
) {
}
