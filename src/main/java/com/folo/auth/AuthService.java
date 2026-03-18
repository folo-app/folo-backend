package com.folo.auth;

import com.folo.common.enums.AuthProvider;
import com.folo.common.exception.ApiException;
import com.folo.common.exception.ErrorCode;
import com.folo.common.util.HandleGenerator;
import com.folo.common.util.HashUtils;
import com.folo.config.EmailVerificationProperties;
import com.folo.config.JwtProperties;
import com.folo.notification.NotificationSetting;
import com.folo.notification.NotificationSettingRepository;
import com.folo.portfolio.Portfolio;
import com.folo.portfolio.PortfolioRepository;
import com.folo.security.FoloUserPrincipal;
import com.folo.security.JwtTokenProvider;
import com.folo.user.User;
import com.folo.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;

@Service
public class AuthService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final UserAuthIdentityRepository userAuthIdentityRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PortfolioRepository portfolioRepository;
    private final NotificationSettingRepository notificationSettingRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailVerificationStore emailVerificationStore;
    private final EmailSender emailSender;
    private final EmailVerificationProperties emailVerificationProperties;
    private final JwtProperties jwtProperties;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthService(
            UserRepository userRepository,
            UserAuthIdentityRepository userAuthIdentityRepository,
            RefreshTokenRepository refreshTokenRepository,
            PortfolioRepository portfolioRepository,
            NotificationSettingRepository notificationSettingRepository,
            PasswordEncoder passwordEncoder,
            EmailVerificationStore emailVerificationStore,
            EmailSender emailSender,
            EmailVerificationProperties emailVerificationProperties,
            JwtProperties jwtProperties,
            JwtTokenProvider jwtTokenProvider
    ) {
        this.userRepository = userRepository;
        this.userAuthIdentityRepository = userAuthIdentityRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.portfolioRepository = portfolioRepository;
        this.notificationSettingRepository = notificationSettingRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailVerificationStore = emailVerificationStore;
        this.emailSender = emailSender;
        this.emailVerificationProperties = emailVerificationProperties;
        this.jwtProperties = jwtProperties;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Transactional
    public SignupResponse signup(SignupRequest request) {
        validatePassword(request.password());
        if (userAuthIdentityRepository.existsByEmail(request.email())) {
            throw new ApiException(ErrorCode.DUPLICATE_EMAIL);
        }
        if (userRepository.existsByNickname(request.nickname())) {
            throw new ApiException(ErrorCode.DUPLICATE_NICKNAME);
        }

        User user = new User(generateUniqueHandle(request.nickname()), request.nickname(), request.profileImage());
        userRepository.save(user);

        UserAuthIdentity identity = new UserAuthIdentity(user, request.email(), passwordEncoder.encode(request.password()));
        userAuthIdentityRepository.save(identity);
        portfolioRepository.save(Portfolio.defaultOf(user));
        notificationSettingRepository.save(NotificationSetting.defaultOf(user));

        sendVerificationCode(request.email(), true);
        return new SignupResponse(user.getId(), user.getNickname(), request.email(), true);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        UserAuthIdentity identity = userAuthIdentityRepository.findByProviderAndProviderUserId(AuthProvider.EMAIL, request.email())
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_CREDENTIALS));

        if (!passwordEncoder.matches(request.password(), identity.getPasswordHash())) {
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS);
        }
        if (!identity.getUser().isActive()) {
            throw new ApiException(ErrorCode.UNAUTHORIZED, "탈퇴한 계정입니다.");
        }
        if (!identity.isEmailVerified()) {
            throw new ApiException(ErrorCode.EMAIL_NOT_VERIFIED);
        }

        return issueTokens(identity, null, null);
    }

    @Transactional
    public void sendVerificationCode(String email, boolean allowBypassCooldown) {
        UserAuthIdentity identity = userAuthIdentityRepository.findByProviderAndProviderUserId(AuthProvider.EMAIL, email)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "이메일 계정을 찾을 수 없습니다."));

        if (identity.isEmailVerified()) {
            return;
        }
        if (!allowBypassCooldown && emailVerificationStore.isResendBlocked(email)) {
            throw new ApiException(ErrorCode.VERIFICATION_RESEND_TOO_SOON);
        }

        String code = generateCode();
        emailVerificationStore.save(
                email,
                code,
                emailVerificationProperties.ttlSeconds(),
                emailVerificationProperties.resendCooldownSeconds()
        );
        emailSender.sendVerificationCode(email, code);
    }

    @Transactional
    public AuthResponse confirmEmail(ConfirmEmailRequest request) {
        UserAuthIdentity identity = userAuthIdentityRepository.findByProviderAndProviderUserId(AuthProvider.EMAIL, request.email())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "이메일 계정을 찾을 수 없습니다."));

        if (!emailVerificationStore.matches(request.email(), request.code())) {
            throw new ApiException(ErrorCode.INVALID_VERIFICATION_CODE);
        }

        identity.verifyEmail();
        emailVerificationStore.clear(request.email());
        return issueTokens(identity, null, null);
    }

    @Transactional
    public AuthResponse refresh(RefreshRequest request) {
        String type = jwtTokenProvider.parse(request.refreshToken()).get("type", String.class);
        if (!"refresh".equals(type)) {
            throw new ApiException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        String hashedToken = HashUtils.sha256(request.refreshToken());
        RefreshToken refreshToken = refreshTokenRepository.findByTokenHash(hashedToken)
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_REFRESH_TOKEN));

        if (refreshToken.isRevoked()) {
            throw new ApiException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
        if (refreshToken.isExpired(LocalDateTime.now())) {
            throw new ApiException(ErrorCode.EXPIRED_REFRESH_TOKEN);
        }

        refreshToken.revoke();
        UserAuthIdentity identity = findEmailIdentity(refreshToken.getUser());
        return createTokens(refreshToken.getUser(), identity, refreshToken.getDeviceId(), refreshToken.getDeviceName());
    }

    @Transactional
    public void logout(Long userId, LogoutRequest request) {
        String hashedToken = HashUtils.sha256(request.refreshToken());
        RefreshToken refreshToken = refreshTokenRepository.findByTokenHash(hashedToken)
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_REFRESH_TOKEN));

        if (!refreshToken.getUser().getId().equals(userId)) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }
        refreshToken.revoke();
    }

    @Transactional
    public void withdraw(Long userId) {
        User user = userRepository.findByIdAndActiveTrue(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "사용자를 찾을 수 없습니다."));
        user.withdraw();
        refreshTokenRepository.findByUserIdAndRevokedAtIsNull(userId).forEach(RefreshToken::revoke);
    }

    private AuthResponse issueTokens(UserAuthIdentity identity, String deviceId, String deviceName) {
        return createTokens(identity.getUser(), identity, deviceId, deviceName);
    }

    private AuthResponse createTokens(User user, UserAuthIdentity identity) {
        return createTokens(user, identity, null, null);
    }

    private AuthResponse createTokens(User user, UserAuthIdentity identity, String deviceId, String deviceName) {
        FoloUserPrincipal principal = new FoloUserPrincipal(user.getId(), identity.getEmail());
        String accessToken = jwtTokenProvider.generateAccessToken(principal);
        String refreshTokenValue = jwtTokenProvider.generateRefreshToken(principal);
        refreshTokenRepository.save(new RefreshToken(
                user,
                HashUtils.sha256(refreshTokenValue),
                LocalDateTime.now().plusSeconds(jwtProperties.refreshTokenExpirationSeconds()),
                deviceId,
                deviceName
        ));
        return new AuthResponse(
                user.getId(),
                user.getNickname(),
                identity.getEmail(),
                user.getProfileImageUrl(),
                accessToken,
                refreshTokenValue
        );
    }

    private UserAuthIdentity findEmailIdentity(User user) {
        return userAuthIdentityRepository.findByUserIdAndProvider(user.getId(), AuthProvider.EMAIL)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "이메일 계정을 찾을 수 없습니다."));
    }

    private void validatePassword(String password) {
        boolean valid = password.matches("^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z\\d]).{8,100}$");
        if (!valid) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "비밀번호는 8자 이상이며 영문, 숫자, 특수문자를 포함해야 합니다.");
        }
    }

    private String generateUniqueHandle(String nickname) {
        String handle = HandleGenerator.fromNickname(nickname);
        while (userRepository.existsByHandle(handle)) {
            handle = HandleGenerator.fromNickname(nickname);
        }
        return handle;
    }

    private String generateCode() {
        int value = RANDOM.nextInt(900_000) + 100_000;
        return Integer.toString(value);
    }
}
