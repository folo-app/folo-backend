package com.folo.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

record SignupRequest(
        @Email @NotBlank String email,
        @NotBlank @Size(min = 8, max = 100) String password,
        @NotBlank @Size(min = 2, max = 20) String nickname,
        @Size(max = 1000) String profileImage
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
        String profileImage,
        String accessToken,
        String refreshToken
) {
}
