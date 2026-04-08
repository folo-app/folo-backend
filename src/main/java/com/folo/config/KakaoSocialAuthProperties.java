package com.folo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.lang.Nullable;

@ConfigurationProperties(prefix = "app.auth.social.kakao")
public record KakaoSocialAuthProperties(
        boolean enabled,
        String clientId,
        @Nullable String clientSecret,
        String redirectUri,
        String authorizationUrl,
        String tokenUrl,
        String userInfoUrl,
        String scope
) {
}
