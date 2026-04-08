package com.folo.auth;

import com.folo.common.api.ApiResponse;
import com.folo.common.enums.AuthProvider;
import com.folo.security.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/auth/social")
public class SocialAuthController {

    private final SocialAuthService socialAuthService;
    private final AppleIdentityTokenVerifier appleIdentityTokenVerifier;

    public SocialAuthController(
            SocialAuthService socialAuthService,
            @Nullable AppleIdentityTokenVerifier appleIdentityTokenVerifier
    ) {
        this.socialAuthService = socialAuthService;
        this.appleIdentityTokenVerifier = appleIdentityTokenVerifier;
    }

    @PostMapping("/{provider}/start")
    public ApiResponse<SocialAuthStartResponse> start(
            @PathVariable String provider,
            @Valid @RequestBody SocialAuthStartRequest request
    ) {
        return ApiResponse.success(
                socialAuthService.start(resolveProvider(provider), request, SecurityUtils.currentUserIdOrNull()),
                "소셜 로그인 화면으로 이동합니다."
        );
    }

    @GetMapping(value = "/{provider}/callback", produces = MediaType.TEXT_HTML_VALUE)
    public String callback(
            @PathVariable String provider,
            @RequestParam Map<String, String> callbackParams
    ) {
        return socialAuthService.renderCallbackPage(resolveProvider(provider), callbackParams);
    }

    @PostMapping("/exchange")
    public ApiResponse<SocialAuthExchangeResponse> exchange(@Valid @RequestBody SocialAuthExchangeRequest request) {
        return ApiResponse.success(
                socialAuthService.exchange(request),
                "소셜 로그인 결과를 확인했습니다."
        );
    }

    @PostMapping("/apple/verify")
    public ApiResponse<SocialAuthExchangeResponse> verifyApple(@Valid @RequestBody AppleNativeAuthRequest request) {
        if (appleIdentityTokenVerifier == null) {
            throw new com.folo.common.exception.ApiException(
                    com.folo.common.exception.ErrorCode.SOCIAL_AUTH_NOT_SUPPORTED,
                    "APPLE 로그인 연동이 아직 준비되지 않았습니다."
            );
        }
        return ApiResponse.success(
                socialAuthService.exchangeIdentityDirect(
                        appleIdentityTokenVerifier.verify(request),
                        request.deviceId(),
                        request.deviceName()
                ),
                "Apple 로그인 결과를 확인했습니다."
        );
    }

    @PostMapping("/complete-profile")
    public ApiResponse<AuthResponse> completeProfile(@Valid @RequestBody SocialAuthCompleteProfileRequest request) {
        return ApiResponse.success(
                socialAuthService.completeProfile(request),
                "소셜 로그인 프로필 설정이 완료되었습니다."
        );
    }

    @PostMapping("/link")
    public ApiResponse<AuthResponse> link(@Valid @RequestBody SocialAuthLinkRequest request) {
        return ApiResponse.success(
                socialAuthService.linkSocialIdentity(SecurityUtils.currentUserId(), request),
                "소셜 로그인이 연결되었습니다."
        );
    }

    private AuthProvider resolveProvider(String provider) {
        try {
            return AuthProvider.fromPath(provider);
        } catch (IllegalArgumentException exception) {
            throw new com.folo.common.exception.ApiException(
                    com.folo.common.exception.ErrorCode.SOCIAL_AUTH_NOT_SUPPORTED,
                    exception.getMessage()
            );
        }
    }
}
