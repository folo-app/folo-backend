package com.folo.auth;

import com.folo.common.enums.AuthProvider;
import org.springframework.lang.Nullable;

enum SocialAuthPlatform {
    IOS,
    ANDROID,
    WEB
}

enum SocialAuthExchangeStatus {
    AUTHENTICATED,
    PROFILE_SETUP_REQUIRED,
    ACCOUNT_LINK_REQUIRED
}

record SocialAuthStartCommand(
        String state,
        SocialAuthPlatform platform,
        @Nullable Long currentUserId,
        @Nullable String deviceId,
        @Nullable String deviceName
) {
}

record SocialAuthStartResult(
        String authorizationUrl,
        @Nullable String codeVerifier,
        @Nullable String nonce
) {
}

record SocialAuthorizationState(
        AuthProvider provider,
        SocialAuthPlatform platform,
        @Nullable Long currentUserId,
        @Nullable String deviceId,
        @Nullable String deviceName,
        @Nullable String codeVerifier,
        @Nullable String nonce
) {
}

record SocialProviderIdentity(
        AuthProvider provider,
        String providerUserId,
        @Nullable String email,
        boolean emailVerified,
        @Nullable String nicknameSuggestion,
        @Nullable String profileImage
) {
}

record SocialHandoffSession(
        SocialAuthExchangeStatus status,
        AuthProvider provider,
        String providerUserId,
        @Nullable String email,
        boolean emailVerified,
        @Nullable String nicknameSuggestion,
        @Nullable String profileImage,
        @Nullable Long existingUserId,
        @Nullable String deviceId,
        @Nullable String deviceName
) {
}

record PendingSocialSignup(
        AuthProvider provider,
        String providerUserId,
        @Nullable String email,
        boolean emailVerified,
        @Nullable String nicknameSuggestion,
        @Nullable String profileImage,
        @Nullable String deviceId,
        @Nullable String deviceName
) {
}

record PendingSocialLink(
        AuthProvider provider,
        String providerUserId,
        @Nullable String email,
        boolean emailVerified,
        @Nullable String nicknameSuggestion,
        @Nullable String profileImage,
        long existingUserId,
        @Nullable String deviceId,
        @Nullable String deviceName
) {
}
