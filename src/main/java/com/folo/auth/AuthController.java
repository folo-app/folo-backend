package com.folo.auth;

import com.folo.common.api.ApiResponse;
import com.folo.security.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
        return ApiResponse.success(authService.signup(request), "회원가입이 완료되었습니다. 이메일 인증을 진행해주세요.");
    }

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request), "로그인되었습니다.");
    }

    @PostMapping("/find-id")
    public ApiResponse<FindLoginIdResponse> findId(@Valid @RequestBody FindLoginIdRequest request) {
        return ApiResponse.success(authService.findLoginId(request), "로그인 아이디 조회가 완료되었습니다.");
    }

    @PostMapping("/password/reset-temp")
    public ApiResponse<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ApiResponse.successMessage("입력한 이메일로 계정이 존재하면 임시 비밀번호를 전송했습니다.");
    }

    @PostMapping("/refresh")
    public ApiResponse<AuthResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ApiResponse.success(authService.refresh(request), "토큰이 갱신되었습니다.");
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(SecurityUtils.currentUserId(), request);
        return ApiResponse.successMessage("로그아웃 되었습니다.");
    }

    @PostMapping("/email/verify")
    public ApiResponse<Void> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        authService.sendVerificationCode(request.email(), false);
        return ApiResponse.successMessage("인증 코드가 발송되었습니다.");
    }

    @PostMapping("/email/confirm")
    public ApiResponse<AuthResponse> confirmEmail(@Valid @RequestBody ConfirmEmailRequest request) {
        return ApiResponse.success(authService.confirmEmail(request), "이메일 인증이 완료되었습니다.");
    }

    @DeleteMapping("/withdraw")
    public ApiResponse<Void> withdraw() {
        authService.withdraw(SecurityUtils.currentUserId());
        return ApiResponse.successMessage("탈퇴 처리되었습니다. 30일 후 데이터가 삭제됩니다.");
    }
}
