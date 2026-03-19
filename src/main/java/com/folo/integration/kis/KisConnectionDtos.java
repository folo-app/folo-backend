package com.folo.integration.kis;

import org.springframework.lang.Nullable;

import java.util.Map;

record KisConnectionStatusResponse(
        boolean connected,
        String phase,
        boolean oauthEnabled,
        boolean clientConfigured,
        boolean connectionAvailable,
        @Nullable String lastSyncedAt,
        String nextStep
) {
}

record KisConnectionStartResponse(
        boolean started,
        String phase,
        @Nullable String authorizationUrl,
        @Nullable String authorizationMethod,
        @Nullable Map<String, String> requestFields,
        @Nullable String state,
        String nextStep
) {
}

record KisConnectionCallbackResponse(
        boolean connected,
        String phase,
        boolean hasAuthorizationCode,
        boolean hasState,
        @Nullable String authorizationCode,
        @Nullable String accountNumber,
        @Nullable String accountProductCode,
        @Nullable String partnerUser,
        @Nullable String personalSecKey,
        @Nullable String providerError,
        String detail
) {
}
