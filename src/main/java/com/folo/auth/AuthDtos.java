package com.folo.auth;

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
        String email,
        @Nullable String profileImage,
        String accessToken,
        String refreshToken
) {
}
