package com.folo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.auth.social.naver")
public record NaverSocialAuthProperties(
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
