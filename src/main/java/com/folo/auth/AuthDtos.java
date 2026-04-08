package com.folo.auth;

import com.folo.common.enums.AuthProvider;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.lang.Nullable;

record SignupRequest(
        @Email @NotBlank String email,
        @NotBlank @Size(min = 8, max = 100) String password,
        @NotBlank @Size(min = 2, max = 20) String nickname,
        @Nullable @Size(max = 1000) String profileImage
) {
}

record SignupResponse(
        Long userId,
        String nickname,
        String email,
        boolean verificationRequired
) {
}

record LoginRequest(
        @Email @NotBlank String email,
        @NotBlank String password
) {
}

record FindLoginIdRequest(
        @NotBlank @Size(min = 2, max = 20) String nickname
) {
}

record FindLoginIdResponse(
        boolean found,
        @Nullable String maskedLoginId
) {
}

record ResetPasswordRequest(
        @Email @NotBlank String email
) {
}

record RefreshRequest(
        @NotBlank String refreshToken
) {
}

record LogoutRequest(
        @NotBlank String refreshToken
) {
}

record VerifyEmailRequest(
        @Email @NotBlank String email
) {
}

record ConfirmEmailRequest(
        @Email @NotBlank String email,
        @NotBlank @Size(min = 6, max = 6) String code
) {
}

record AuthResponse(
        Long userId,
        String nickname,
        @Nullable String email,
        @Nullable String profileImage,
        AuthProvider authProvider,
        String accessToken,
        String refreshToken
) {
}

record SocialAuthStartRequest(
        @Nullable SocialAuthPlatform platform,
        @Nullable @Size(max = 255) String deviceId,
        @Nullable @Size(max = 255) String deviceName
) {
}

record SocialAuthStartResponse(
        String provider,
        String authorizationUrl,
        String state
) {
}

record SocialAuthExchangeRequest(
        @NotBlank String handoffCode,
        @Nullable @Size(max = 255) String deviceId,
        @Nullable @Size(max = 255) String deviceName
) {
}

record SocialAuthExchangeResponse(
        SocialAuthExchangeStatus status,
        @Nullable AuthResponse session,
        @Nullable String pendingToken,
        @Nullable AuthProvider provider,
        @Nullable String email,
        @Nullable String nicknameSuggestion,
        @Nullable String profileImage,
        @Nullable String message
) {
    static SocialAuthExchangeResponse authenticated(AuthResponse session) {
        return new SocialAuthExchangeResponse(
                SocialAuthExchangeStatus.AUTHENTICATED,
                session,
                null,
                session.authProvider(),
                session.email(),
                session.nickname(),
                session.profileImage(),
                null
        );
    }

    static SocialAuthExchangeResponse profileSetupRequired(
            String pendingToken,
            AuthProvider provider,
            @Nullable String email,
            @Nullable String nicknameSuggestion,
            @Nullable String profileImage
    ) {
        return new SocialAuthExchangeResponse(
                SocialAuthExchangeStatus.PROFILE_SETUP_REQUIRED,
                null,
                pendingToken,
                provider,
                email,
                nicknameSuggestion,
                profileImage,
                "추가 프로필 정보를 입력하면 가입이 완료됩니다."
        );
    }

    static SocialAuthExchangeResponse accountLinkRequired(
            String pendingToken,
            AuthProvider provider,
            @Nullable String email,
            @Nullable String nicknameSuggestion,
            @Nullable String profileImage,
            String message
    ) {
        return new SocialAuthExchangeResponse(
                SocialAuthExchangeStatus.ACCOUNT_LINK_REQUIRED,
                null,
                pendingToken,
                provider,
                email,
                nicknameSuggestion,
                profileImage,
                message
        );
    }
}

record SocialAuthCompleteProfileRequest(
        @NotBlank String pendingToken,
        @NotBlank @Size(min = 2, max = 20) String nickname,
        @Nullable @Size(max = 1000) String profileImage
) {
}

record SocialAuthLinkRequest(
        @NotBlank String pendingToken
) {
}
