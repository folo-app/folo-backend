package com.folo.portfolio;

import org.springframework.lang.Nullable;

public record KisSyncSession(
        String serviceAppKey,
        String serviceAppSecret,
        @Nullable String userAccessToken,
        @Nullable String userRefreshToken,
        @Nullable String personalSecretKey,
        @Nullable String accountNumber,
        @Nullable String accountProductCode
) {
}
