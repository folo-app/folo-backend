package com.folo.integration.kis;

import jakarta.validation.constraints.NotBlank;
import org.springframework.lang.Nullable;

import java.util.Map;

record KisConnectionStartRequest(
        @NotBlank String customerName,
        @NotBlank String phoneNumber
) {
}

record KisConnectionStatusResponse(
        boolean connected,
        String phase,
        boolean oauthEnabled,
        boolean clientConfigured,
        boolean connectionAvailable,
        @Nullable String lastSyncedAt,
        @Nullable String connectedAt,
        @Nullable String connectedAccount,
        String nextStep
) {
}

record KisConnectionStartResponse(
        boolean started,
        String phase,
        @Nullable String authorizationUrl,
        @Nullable String launchUrl,
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
