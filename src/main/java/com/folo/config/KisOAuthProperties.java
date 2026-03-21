package com.folo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.lang.Nullable;

@ConfigurationProperties(prefix = "integration.kis.oauth")
public record KisOAuthProperties(
        boolean enabled,
        @Nullable String baseUrl,
        @Nullable String appKey,
        @Nullable String appSecret,
        @Nullable String redirectUri,
        @Nullable String appRedirectUrl,
        @Nullable String corpNo,
        @Nullable String corpName,
        @Nullable String contractType
) {
}
