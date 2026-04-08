package com.folo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.auth.social.google")
public record GoogleSocialAuthProperties(
        boolean enabled,
        String clientId,
        String clientSecret,
        String redirectUri,
        String authorizationUrl,
        String tokenUrl,
        String userInfoUrl,
        String scope
) {
}
