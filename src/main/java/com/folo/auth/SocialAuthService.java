package com.folo.auth;

import com.folo.common.enums.AuthProvider;
import com.folo.common.exception.ApiException;
import com.folo.common.exception.ErrorCode;
import com.folo.common.util.HandleGenerator;
import com.folo.config.SocialAuthProperties;
import com.folo.notification.NotificationSetting;
import com.folo.notification.NotificationSettingRepository;
import com.folo.portfolio.Portfolio;
import com.folo.portfolio.PortfolioRepository;
import com.folo.user.User;
import com.folo.user.UserRepository;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class SocialAuthService {

    private final Map<AuthProvider, SocialAuthProviderClient> providerClients;
    private final SocialAuthFlowStore socialAuthFlowStore;
    private final SocialAuthProperties socialAuthProperties;
    private final UserRepository userRepository;
    private final UserAuthIdentityRepository userAuthIdentityRepository;
    private final PortfolioRepository portfolioRepository;
    private final NotificationSettingRepository notificationSettingRepository;
    private final AuthService authService;

    public SocialAuthService(
            List<SocialAuthProviderClient> providerClients,
            SocialAuthFlowStore socialAuthFlowStore,
            SocialAuthProperties socialAuthProperties,
            UserRepository userRepository,
            UserAuthIdentityRepository userAuthIdentityRepository,
            PortfolioRepository portfolioRepository,
            NotificationSettingRepository notificationSettingRepository,
            AuthService authService
    ) {
        this.providerClients = providerClients.stream().collect(java.util.stream.Collectors.toMap(
                SocialAuthProviderClient::provider,
                client -> client
        ));
        this.socialAuthFlowStore = socialAuthFlowStore;
        this.socialAuthProperties = socialAuthProperties;
        this.userRepository = userRepository;
        this.userAuthIdentityRepository = userAuthIdentityRepository;
        this.portfolioRepository = portfolioRepository;
        this.notificationSettingRepository = notificationSettingRepository;
        this.authService = authService;
    }

    @Transactional(readOnly = true)
    public SocialAuthStartResponse start(AuthProvider provider, SocialAuthStartRequest request, @Nullable Long currentUserId) {
        SocialAuthProviderClient client = getRequiredClient(provider);
        String state = UUID.randomUUID().toString();
        SocialAuthStartCommand command = new SocialAuthStartCommand(
                state,
                request.platform() != null ? request.platform() : SocialAuthPlatform.IOS,
                currentUserId,
                request.deviceId(),
                request.deviceName()
        );
        SocialAuthStartResult result = client.start(command);
        socialAuthFlowStore.saveAuthorizationState(
                state,
                new SocialAuthorizationState(
                        provider,
                        command.platform(),
                        currentUserId,
                        request.deviceId(),
                        request.deviceName(),
                        result.codeVerifier(),
                        result.nonce()
                ),
                socialAuthProperties.authorizationStateTtlSeconds()
        );
        return new SocialAuthStartResponse(provider.name(), result.authorizationUrl(), state);
    }

    @Transactional
    public String renderCallbackPage(AuthProvider provider, Map<String, String> callbackParams) {
        SocialAuthorizationState state = consumeAuthorizationState(callbackParams.get("state"));
        if (state.provider() != provider) {
            throw new ApiException(ErrorCode.INVALID_SOCIAL_AUTH_STATE);
        }
        SocialProviderIdentity identity = getRequiredClient(provider).completeAuthorization(state, callbackParams);
        String redirectUrl = finalizeLogin(identity, state.deviceId(), state.deviceName());
        return buildCompletionHtml(true, provider.name() + " 로그인 완료", "앱으로 다시 돌아갑니다.", redirectUrl);
    }

    @Transactional
    public String finalizeLogin(
            SocialProviderIdentity identity,
            @Nullable String deviceId,
            @Nullable String deviceName
    ) {
        SocialHandoffSession handoffSession = buildHandoffSession(identity, deviceId, deviceName);
        String handoffCode = UUID.randomUUID().toString();
        socialAuthFlowStore.saveHandoffSession(
                handoffCode,
                handoffSession,
                socialAuthProperties.handoffCodeTtlSeconds()
        );
        return UriComponentsBuilder.fromUriString(socialAuthProperties.appRedirectUrl())
                .queryParam("handoffCode", handoffCode)
                .queryParam("status", handoffSession.status().name())
                .build(true)
                .toUriString();
    }

    private SocialHandoffSession buildHandoffSession(
            SocialProviderIdentity identity,
            @Nullable String deviceId,
            @Nullable String deviceName
    ) {
        UserAuthIdentity existingIdentity = userAuthIdentityRepository.findByProviderAndProviderUserId(
                identity.provider(),
                identity.providerUserId()
        ).orElse(null);

        if (existingIdentity != null) {
            ensureUserActive(existingIdentity.getUser());
            return new SocialHandoffSession(
                    SocialAuthExchangeStatus.AUTHENTICATED,
                    identity.provider(),
                    identity.providerUserId(),
                    existingIdentity.getEmail(),
                    existingIdentity.isEmailVerified(),
                    existingIdentity.getUser().getNickname(),
                    existingIdentity.getUser().getProfileImageUrl(),
                    existingIdentity.getUser().getId(),
                    deviceId,
                    deviceName
            );
        }

        UserAuthIdentity emailIdentity = identity.email() != null
                ? userAuthIdentityRepository.findByEmail(identity.email()).orElse(null)
                : null;
        if (emailIdentity != null) {
            ensureUserActive(emailIdentity.getUser());
            return new SocialHandoffSession(
                    SocialAuthExchangeStatus.ACCOUNT_LINK_REQUIRED,
                    identity.provider(),
                    identity.providerUserId(),
                    identity.email(),
                    identity.emailVerified(),
                    identity.nicknameSuggestion(),
                    identity.profileImage(),
                    emailIdentity.getUser().getId(),
                    deviceId,
                    deviceName
            );
        }

        return new SocialHandoffSession(
                SocialAuthExchangeStatus.PROFILE_SETUP_REQUIRED,
                identity.provider(),
                identity.providerUserId(),
                identity.email(),
                identity.emailVerified(),
                identity.nicknameSuggestion(),
                identity.profileImage(),
                null,
                deviceId,
                deviceName
        );
    }

    @Transactional
    public SocialAuthExchangeResponse exchange(SocialAuthExchangeRequest request) {
        SocialHandoffSession handoffSession = consumeHandoffSession(request.handoffCode());
        return toExchangeResponse(handoffSession, request.deviceId(), request.deviceName());
    }

    private SocialAuthExchangeResponse toExchangeResponse(
            SocialHandoffSession handoffSession,
            @Nullable String requestedDeviceId,
            @Nullable String requestedDeviceName
    ) {
        String nextDeviceId = requestedDeviceId != null ? requestedDeviceId : handoffSession.deviceId();
        String nextDeviceName = requestedDeviceName != null ? requestedDeviceName : handoffSession.deviceName();

        if (handoffSession.status() == SocialAuthExchangeStatus.AUTHENTICATED) {
            UserAuthIdentity identity = userAuthIdentityRepository.findByProviderAndProviderUserId(
                    handoffSession.provider(),
                    handoffSession.providerUserId()
            ).orElseThrow(() -> new ApiException(ErrorCode.INVALID_SOCIAL_AUTH_HANDOFF));
            AuthResponse session = authService.issueSession(identity, nextDeviceId, nextDeviceName);
            return SocialAuthExchangeResponse.authenticated(session);
        }

        if (handoffSession.status() == SocialAuthExchangeStatus.ACCOUNT_LINK_REQUIRED) {
            String pendingToken = UUID.randomUUID().toString();
            socialAuthFlowStore.savePendingLink(
                    pendingToken,
                    new PendingSocialLink(
                            handoffSession.provider(),
                            handoffSession.providerUserId(),
                            handoffSession.email(),
                            handoffSession.emailVerified(),
                            handoffSession.nicknameSuggestion(),
                            handoffSession.profileImage(),
                            handoffSession.existingUserId() != null ? handoffSession.existingUserId() : 0L,
                            nextDeviceId,
                            nextDeviceName
                    ),
                    socialAuthProperties.pendingFlowTtlSeconds()
            );
            return SocialAuthExchangeResponse.accountLinkRequired(
                    pendingToken,
                    handoffSession.provider(),
                    handoffSession.email(),
                    handoffSession.nicknameSuggestion(),
                    handoffSession.profileImage(),
                    "기존 계정 확인 후 소셜 로그인을 연결해 주세요."
            );
        }

        String pendingToken = UUID.randomUUID().toString();
        socialAuthFlowStore.savePendingSignup(
                pendingToken,
                new PendingSocialSignup(
                        handoffSession.provider(),
                        handoffSession.providerUserId(),
                        handoffSession.email(),
                        handoffSession.emailVerified(),
                        handoffSession.nicknameSuggestion(),
                        handoffSession.profileImage(),
                        nextDeviceId,
                        nextDeviceName
                ),
                socialAuthProperties.pendingFlowTtlSeconds()
        );
        return SocialAuthExchangeResponse.profileSetupRequired(
                pendingToken,
                handoffSession.provider(),
                handoffSession.email(),
                handoffSession.nicknameSuggestion(),
                handoffSession.profileImage()
        );
    }

    @Transactional
    public AuthResponse completeProfile(SocialAuthCompleteProfileRequest request) {
        PendingSocialSignup pendingSignup = consumePendingSignup(request.pendingToken());
        if (pendingSignup.email() != null && userAuthIdentityRepository.existsByEmail(pendingSignup.email())) {
            throw new ApiException(ErrorCode.DUPLICATE_EMAIL);
        }
        if (userRepository.existsByNickname(request.nickname())) {
            throw new ApiException(ErrorCode.DUPLICATE_NICKNAME);
        }

        User user = new User(generateUniqueHandle(request.nickname()), request.nickname(), request.profileImage());
        userRepository.save(user);

        UserAuthIdentity identity = new UserAuthIdentity(
                user,
                pendingSignup.provider(),
                pendingSignup.providerUserId(),
                pendingSignup.email(),
                pendingSignup.emailVerified()
        );
        userAuthIdentityRepository.save(identity);
        portfolioRepository.save(Portfolio.defaultOf(user));
        notificationSettingRepository.save(NotificationSetting.defaultOf(user));

        return authService.issueSession(identity, pendingSignup.deviceId(), pendingSignup.deviceName());
    }

    @Transactional
    public AuthResponse linkSocialIdentity(Long userId, SocialAuthLinkRequest request) {
        PendingSocialLink pendingLink = consumePendingLink(request.pendingToken());
        User user = userRepository.findByIdAndActiveTrue(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "사용자를 찾을 수 없습니다."));

        UserAuthIdentity existingIdentity = userAuthIdentityRepository.findByProviderAndProviderUserId(
                pendingLink.provider(),
                pendingLink.providerUserId()
        ).orElse(null);
        if (existingIdentity != null) {
            if (existingIdentity.getUser().getId().equals(userId)) {
                return authService.issueSession(existingIdentity, pendingLink.deviceId(), pendingLink.deviceName());
            }
            throw new ApiException(ErrorCode.DUPLICATE_SOCIAL_IDENTITY);
        }
        if (userAuthIdentityRepository.existsByUserIdAndProvider(userId, pendingLink.provider())) {
            throw new ApiException(ErrorCode.DUPLICATE_SOCIAL_IDENTITY, "이미 같은 종류의 소셜 로그인이 연결되어 있습니다.");
        }

        UserAuthIdentity identity = new UserAuthIdentity(
                user,
                pendingLink.provider(),
                pendingLink.providerUserId(),
                pendingLink.email(),
                pendingLink.emailVerified()
        );
        userAuthIdentityRepository.save(identity);
        return authService.issueSession(identity, pendingLink.deviceId(), pendingLink.deviceName());
    }

    private SocialAuthProviderClient getRequiredClient(AuthProvider provider) {
        SocialAuthProviderClient client = providerClients.get(provider);
        if (client == null) {
            throw new ApiException(ErrorCode.SOCIAL_AUTH_NOT_SUPPORTED, provider.name() + " 로그인 연동이 아직 준비되지 않았습니다.");
        }
        return client;
    }

    private SocialAuthorizationState consumeAuthorizationState(@Nullable String state) {
        if (state == null || state.isBlank()) {
            throw new ApiException(ErrorCode.INVALID_SOCIAL_AUTH_STATE);
        }
        SocialAuthorizationState payload = socialAuthFlowStore.consumeAuthorizationState(state);
        if (payload == null) {
            throw new ApiException(ErrorCode.EXPIRED_SOCIAL_AUTH_STATE);
        }
        return payload;
    }

    private SocialHandoffSession consumeHandoffSession(String handoffCode) {
        SocialHandoffSession payload = socialAuthFlowStore.consumeHandoffSession(handoffCode);
        if (payload == null) {
            throw new ApiException(ErrorCode.EXPIRED_SOCIAL_AUTH_HANDOFF);
        }
        return payload;
    }

    private PendingSocialSignup consumePendingSignup(String pendingToken) {
        PendingSocialSignup payload = socialAuthFlowStore.consumePendingSignup(pendingToken);
        if (payload == null) {
            throw new ApiException(ErrorCode.EXPIRED_SOCIAL_AUTH_PENDING);
        }
        return payload;
    }

    private PendingSocialLink consumePendingLink(String pendingToken) {
        PendingSocialLink payload = socialAuthFlowStore.consumePendingLink(pendingToken);
        if (payload == null) {
            throw new ApiException(ErrorCode.EXPIRED_SOCIAL_AUTH_PENDING);
        }
        return payload;
    }

    private void ensureUserActive(User user) {
        if (!user.isActive()) {
            throw new ApiException(ErrorCode.UNAUTHORIZED, "탈퇴한 계정입니다.");
        }
    }

    private String buildCompletionHtml(boolean success, String title, String detail, @Nullable String redirectUrl) {
        String safeTitle = HtmlUtils.htmlEscape(title);
        String safeDetail = HtmlUtils.htmlEscape(detail);
        String safeRedirectUrl = redirectUrl != null ? HtmlUtils.htmlEscape(redirectUrl) : "";
        String actionText = success ? "앱으로 돌아가는 중..." : "앱으로 돌아가기";
        String autoRedirectScript = redirectUrl == null
                ? ""
                : """
                        <script>
                          setTimeout(function () { window.location.href = '%s'; }, 300);
                        </script>
                        """.formatted(safeRedirectUrl);
        String actionLink = redirectUrl == null
                ? ""
                : """
                        <a class="button" href="%s">%s</a>
                        """.formatted(safeRedirectUrl, actionText);
        return """
                <!doctype html>
                <html lang="ko">
                <head>
                  <meta charset="utf-8" />
                  <meta name="viewport" content="width=device-width, initial-scale=1" />
                  <title>%s</title>
                  <style>
                    body { margin: 0; font-family: -apple-system, BlinkMacSystemFont, 'Apple SD Gothic Neo', sans-serif; background: #07090c; color: #f5f7fb; }
                    main { min-height: 100vh; display: flex; align-items: center; justify-content: center; padding: 24px; }
                    section { width: min(420px, 100%%); background: #131821; border-radius: 24px; padding: 28px; box-shadow: 0 24px 80px rgba(0,0,0,0.35); }
                    h1 { margin: 0 0 12px; font-size: 24px; }
                    p { margin: 0 0 20px; color: #b7c0cf; line-height: 1.5; }
                    .button { display: inline-flex; align-items: center; justify-content: center; min-height: 48px; padding: 0 18px; border-radius: 999px; background: #f4f7fb; color: #111827; text-decoration: none; font-weight: 700; }
                  </style>
                  %s
                </head>
                <body>
                  <main>
                    <section>
                      <h1>%s</h1>
                      <p>%s</p>
                      %s
                    </section>
                  </main>
                </body>
                </html>
                """.formatted(safeTitle, autoRedirectScript, safeTitle, safeDetail, actionLink);
    }

    private String generateUniqueHandle(String nickname) {
        String handle = HandleGenerator.fromNickname(nickname);
        while (userRepository.existsByHandle(handle)) {
            handle = HandleGenerator.fromNickname(nickname);
        }
        return handle;
    }
}
