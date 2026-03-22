package com.folo.integration.kis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.folo.common.exception.ApiException;
import com.folo.common.exception.ErrorCode;
import com.folo.config.KisOAuthProperties;
import com.folo.security.FieldEncryptor;
import com.folo.user.User;
import com.folo.user.UserRepository;
import org.springframework.http.MediaType;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.HtmlUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class KisConnectionService {

    private static final Duration STATE_TTL = Duration.ofMinutes(10);
    private static final DateTimeFormatter KIS_EXPIRY_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final RestClient restClient;
    private final KisOAuthProperties properties;
    private final UserRepository userRepository;
    private final FieldEncryptor fieldEncryptor;
    private final ObjectMapper objectMapper;

    public KisConnectionService(
            RestClient.Builder restClientBuilder,
            KisOAuthProperties properties,
            UserRepository userRepository,
            FieldEncryptor fieldEncryptor,
            ObjectMapper objectMapper
    ) {
        this.restClient = restClientBuilder.build();
        this.properties = properties;
        this.userRepository = userRepository;
        this.fieldEncryptor = fieldEncryptor;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public KisConnectionStatusResponse getStatus(Long userId) {
        User user = getRequiredUser(userId);
        boolean configured = isConfigured();
        boolean available = properties.enabled() && configured;
        boolean connected = hasConnection(user);
        String phase = connected
                ? "CONNECTED"
                : available ? "READY" : properties.enabled() ? "CONFIG_MISSING" : "PREPARING";
        String nextStep = connected
                ? "한국투자 연결이 완료되었습니다. 포트폴리오 동기화를 바로 시도할 수 있습니다."
                : available
                ? "실명과 휴대폰번호를 입력한 뒤 KIS 인증 화면으로 이동하세요."
                : "서비스 공용 KIS 앱키, 시크릿, redirect URI, corp number를 설정해야 합니다.";

        return new KisConnectionStatusResponse(
                connected,
                phase,
                properties.enabled(),
                configured,
                available,
                null,
                user.getKisConnectedAt() != null ? user.getKisConnectedAt().toString() : null,
                maskAccount(fieldEncryptor.decrypt(user.getKisAccountNumberEncrypted()), user.getKisAccountProductCode()),
                nextStep
        );
    }

    @Transactional(readOnly = true)
    public KisConnectionStartResponse start(Long userId, KisConnectionStartRequest request) {
        validateStartRequest(request);
        boolean configured = isConfigured();
        boolean available = properties.enabled() && configured;
        if (!available) {
            return new KisConnectionStartResponse(
                    false,
                    properties.enabled() ? "CONFIG_MISSING" : "PREPARING",
                    null,
                    null,
                    null,
                    null,
                    null,
                    "KIS OAuth 환경 설정이 아직 준비되지 않았습니다."
            );
        }

        String state = createState(userId, request.customerName().trim(), normalizePhoneNumber(request.phoneNumber()));
        Map<String, String> requestFields = buildRequestFields(
                userId,
                request.customerName().trim(),
                normalizePhoneNumber(request.phoneNumber()),
                state
        );

        return new KisConnectionStartResponse(
                true,
                "READY",
                properties.baseUrl() + "/oauth2/authorizeP",
                buildAuthorizeLaunchUrl(state),
                "GET",
                requestFields,
                state,
                "브라우저에서 한국투자 인증을 완료하면 앱으로 다시 돌아옵니다."
        );
    }

    @Transactional(readOnly = true)
    public String renderAuthorizationPage(String state) {
        KisConnectionStatePayload payload = decodeState(state);
        Map<String, String> requestFields = buildRequestFields(
                payload.userId(),
                payload.customerName(),
                payload.phoneNumber(),
                state
        );
        return buildAutoSubmitHtml(properties.baseUrl() + "/oauth2/authorizeP", requestFields);
    }

    @Transactional
    public String renderCallbackPage(Map<String, String> rawValues) {
        try {
            KisConnectionCallbackResponse response = completeCallback(rawValues);
            return buildCompletionHtml(
                    true,
                    "한국투자 연결 완료",
                    response.detail(),
                    buildAppRedirectUrl(true, response.detail())
            );
        } catch (ApiException exception) {
            return buildCompletionHtml(
                    false,
                    "한국투자 연결 실패",
                    exception.getMessage(),
                    buildAppRedirectUrl(false, exception.getMessage())
            );
        } catch (Exception exception) {
            return buildCompletionHtml(
                    false,
                    "한국투자 연결 실패",
                    "콜백 처리 중 오류가 발생했습니다.",
                    buildAppRedirectUrl(false, "콜백 처리 중 오류가 발생했습니다.")
            );
        }
    }

    @Transactional
    public void disconnect(Long userId) {
        User user = getRequiredUser(userId);
        String accessToken = fieldEncryptor.decrypt(user.getKisAccessTokenEncrypted());
        if (StringUtils.hasText(accessToken) && isConfigured()) {
            try {
                URI requestUri = UriComponentsBuilder.fromUriString(properties.baseUrl())
                        .path("/oauth2/revokeP")
                        .build(true)
                        .toUri();

                restClient.post()
                        .uri(requestUri)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of(
                                "appkey", properties.appKey(),
                                "appsecret", properties.appSecret(),
                                "token", accessToken
                        ))
                        .retrieve()
                        .toBodilessEntity();
            } catch (RuntimeException ignored) {
                // 연결 해제는 로컬 저장소 정리가 우선이므로 provider revoke 실패는 무시한다.
            }
        }
        user.disconnectKis();
    }

    private KisConnectionCallbackResponse completeCallback(Map<String, String> rawValues) {
        String providerError = firstNonBlank(
                decodeValue(rawValues.get("error")),
                decodeValue(rawValues.get("error_description")),
                decodeValue(rawValues.get("message"))
        );
        if (StringUtils.hasText(providerError)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, providerError);
        }

        String state = decodeValue(rawValues.get("state"));
        String authorizationCode = decodeValue(rawValues.get("authorization_code"));
        String accountNumber = decodeValue(rawValues.get("acctno"));
        String accountProductCode = decodeValue(rawValues.get("acct_prdt_cd"));
        String partnerUser = decodeValue(rawValues.get("partner_user"));
        String personalSecKey = decodeValue(rawValues.get("personalseckey"));

        if (!StringUtils.hasText(state)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "KIS callback state가 비어 있습니다.");
        }
        if (!StringUtils.hasText(authorizationCode) || !StringUtils.hasText(personalSecKey)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "KIS callback 인증 정보가 누락되었습니다.");
        }

        KisConnectionStatePayload payload = decodeState(state);
        String expectedPartnerUser = buildPartnerUser(payload.userId());
        if (StringUtils.hasText(partnerUser) && !expectedPartnerUser.equals(partnerUser)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "KIS callback partner_user가 일치하지 않습니다.");
        }

        KisOAuthTokenResponse tokenResponse = exchangeAuthorizationCode(authorizationCode, personalSecKey);
        User user = getRequiredUser(payload.userId());
        user.connectKis(
                fieldEncryptor.encrypt(tokenResponse.accessToken()),
                fieldEncryptor.encrypt(tokenResponse.refreshToken()),
                fieldEncryptor.encrypt(personalSecKey),
                fieldEncryptor.encrypt(accountNumber),
                StringUtils.hasText(accountProductCode) ? accountProductCode : null,
                parseExpiry(tokenResponse.accessTokenExpiresAt(), tokenResponse.accessTokenExpiresIn()),
                parseExpiry(tokenResponse.refreshTokenExpiresAt(), tokenResponse.refreshTokenExpiresIn()),
                LocalDateTime.now()
        );

        return new KisConnectionCallbackResponse(
                true,
                "CONNECTED",
                true,
                true,
                null,
                maskAccount(accountNumber, accountProductCode),
                accountProductCode,
                expectedPartnerUser,
                null,
                null,
                "KIS 연결이 완료되었습니다. 앱으로 돌아가 포트폴리오 동기화를 진행하세요."
        );
    }

    private KisOAuthTokenResponse exchangeAuthorizationCode(String authorizationCode, String personalSecKey) {
        URI requestUri = UriComponentsBuilder.fromUriString(properties.baseUrl())
                .path("/oauth2/tokenP")
                .build(true)
                .toUri();

        KisOAuthTokenResponse response = restClient.post()
                .uri(requestUri)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "appkey", properties.appKey(),
                        "appsecret", properties.appSecret(),
                        "grant_type", "authorization_code",
                        "personalseckey", personalSecKey,
                        "code", authorizationCode,
                        "corpname", StringUtils.hasText(properties.corpNo()) ? properties.corpNo() : properties.corpName()
                ))
                .retrieve()
                .body(KisOAuthTokenResponse.class);

        if (response == null || !StringUtils.hasText(response.accessToken())) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "KIS access token 응답이 비어 있습니다.");
        }

        return response;
    }

    private boolean isConfigured() {
        return StringUtils.hasText(properties.baseUrl())
                && StringUtils.hasText(properties.corpName())
                && StringUtils.hasText(properties.contractType())
                && StringUtils.hasText(properties.appKey())
                && StringUtils.hasText(properties.appSecret())
                && StringUtils.hasText(properties.redirectUri())
                && StringUtils.hasText(properties.corpNo());
    }

    private boolean hasConnection(User user) {
        return StringUtils.hasText(user.getKisAccessTokenEncrypted())
                && StringUtils.hasText(user.getKisPersonalSecretKeyEncrypted());
    }

    private User getRequiredUser(Long userId) {
        return userRepository.findByIdAndActiveTrue(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "사용자를 찾을 수 없습니다."));
    }

    private void validateStartRequest(KisConnectionStartRequest request) {
        if (!StringUtils.hasText(request.customerName()) || request.customerName().trim().length() < 2) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "이름은 2자 이상 입력해주세요.");
        }
        normalizePhoneNumber(request.phoneNumber());
    }

    private String createState(Long userId, String customerName, String phoneNumber) {
        try {
            return fieldEncryptor.encrypt(objectMapper.writeValueAsString(
                    new KisConnectionStatePayload(
                            userId,
                            customerName,
                            phoneNumber,
                            UUID.randomUUID().toString(),
                            LocalDateTime.now()
                    )));
        } catch (Exception exception) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "KIS OAuth state 생성에 실패했습니다.");
        }
    }

    private KisConnectionStatePayload decodeState(String state) {
        try {
            String decrypted = fieldEncryptor.decrypt(state);
            KisConnectionStatePayload payload =
                    objectMapper.readValue(decrypted, KisConnectionStatePayload.class);
            if (payload.issuedAt().plus(STATE_TTL).isBefore(LocalDateTime.now())) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR, "KIS OAuth state가 만료되었습니다.");
            }
            return payload;
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "유효하지 않은 KIS OAuth state입니다.");
        }
    }

    private Map<String, String> buildRequestFields(
            Long userId,
            String customerName,
            String phoneNumber,
            String state
    ) {
        Map<String, String> requestFields = new LinkedHashMap<>();
        requestFields.put("appkey", properties.appKey());
        requestFields.put("redirect_uri", properties.redirectUri());
        requestFields.put("response_type", "code");
        requestFields.put("service", "2");
        requestFields.put("corpno", properties.corpNo());
        requestFields.put("partner_user", buildPartnerUser(userId));
        requestFields.put("personalname", customerName);
        requestFields.put("personalphone", phoneNumber);
        requestFields.put("roption", "P");
        requestFields.put("corp_name", properties.corpName());
        requestFields.put("state", state);
        requestFields.put("contract_type", properties.contractType());
        requestFields.put("isa_yn", "N");
        requestFields.put("reregist_yn", "N");
        return requestFields;
    }

    private String buildAuthorizeLaunchUrl(String state) {
        String redirectUri = properties.redirectUri();
        if (!StringUtils.hasText(redirectUri)) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "KIS redirect URI가 설정되지 않았습니다.");
        }

        if (redirectUri.endsWith("/callback")) {
            return UriComponentsBuilder
                    .fromUriString(redirectUri.substring(0, redirectUri.length() - "/callback".length()) + "/authorize")
                    .queryParam("state", state)
                    .build()
                    .encode()
                    .toUriString();
        }

        return UriComponentsBuilder
                .fromUriString(redirectUri)
                .replacePath("/api/integrations/kis/connect/authorize")
                .queryParam("state", state)
                .build()
                .encode()
                .toUriString();
    }

    private String buildPartnerUser(Long userId) {
        return "folo-user-" + userId;
    }

    private String buildAutoSubmitHtml(String actionUrl, Map<String, String> fields) {
        StringBuilder inputs = new StringBuilder();
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            inputs.append("<input type=\"hidden\" name=\"")
                    .append(HtmlUtils.htmlEscape(entry.getKey()))
                    .append("\" value=\"")
                    .append(HtmlUtils.htmlEscape(entry.getValue()))
                    .append("\" />");
        }

        return """
                <!doctype html>
                <html lang="ko">
                  <head>
                    <meta charset="utf-8" />
                    <meta name="viewport" content="width=device-width, initial-scale=1" />
                    <title>FOLO KIS 연결</title>
                    <style>
                      body { font-family: -apple-system, BlinkMacSystemFont, sans-serif; padding: 32px; background: #f8fafc; color: #0f172a; }
                      .card { max-width: 420px; margin: 80px auto; background: white; border-radius: 24px; padding: 28px; border: 1px solid #e2e8f0; }
                      .title { font-size: 24px; font-weight: 800; margin-bottom: 12px; }
                      .body { font-size: 15px; line-height: 1.6; color: #475569; }
                    </style>
                  </head>
                  <body onload="document.getElementById('kis-connect-form').submit()">
                    <div class="card">
                      <div class="title">한국투자 연결 중</div>
                      <div class="body">잠시 후 한국투자 인증 화면으로 이동합니다.</div>
                    </div>
                    <form id="kis-connect-form" action="%s" method="post">%s</form>
                  </body>
                </html>
                """.formatted(HtmlUtils.htmlEscape(actionUrl), inputs);
    }

    private String buildCompletionHtml(
            boolean success,
            String title,
            String message,
            String appRedirectUrl
    ) {
        String escapedHref = HtmlUtils.htmlEscape(appRedirectUrl);
        String jsRedirectUrl = appRedirectUrl
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");

        return """
                <!doctype html>
                <html lang="ko">
                  <head>
                    <meta charset="utf-8" />
                    <meta name="viewport" content="width=device-width, initial-scale=1" />
                    <title>%s</title>
                    <style>
                      body { font-family: -apple-system, BlinkMacSystemFont, sans-serif; padding: 32px; background: #f8fafc; color: #0f172a; }
                      .card { max-width: 460px; margin: 80px auto; background: white; border-radius: 24px; padding: 28px; border: 1px solid #e2e8f0; }
                      .title { font-size: 24px; font-weight: 800; margin-bottom: 12px; color: %s; }
                      .body { font-size: 15px; line-height: 1.6; color: #475569; margin-bottom: 20px; }
                      .button { display: inline-block; border-radius: 999px; padding: 14px 20px; background: #0f172a; color: white; text-decoration: none; font-weight: 700; }
                    </style>
                  </head>
                  <body>
                    <div class="card">
                      <div class="title">%s</div>
                      <div class="body">%s</div>
                      <a class="button" href="%s">앱으로 돌아가기</a>
                    </div>
                    <script>
                      setTimeout(function () {
                        window.location.href = %s;
                      }, 400);
                    </script>
                  </body>
                </html>
                """.formatted(
                HtmlUtils.htmlEscape(title),
                success ? "#0f766e" : "#b91c1c",
                HtmlUtils.htmlEscape(title),
                HtmlUtils.htmlEscape(message),
                escapedHref,
                "\"" + jsRedirectUrl + "\""
        );
    }

    private String buildAppRedirectUrl(boolean success, String message) {
        String baseUrl = StringUtils.hasText(properties.appRedirectUrl())
                ? properties.appRedirectUrl()
                : "folo://kis/callback";
        return UriComponentsBuilder.fromUriString(baseUrl)
                .queryParam("status", success ? "success" : "error")
                .queryParam("message", message)
                .build()
                .encode()
                .toUriString();
    }

    private String normalizePhoneNumber(String rawPhoneNumber) {
        if (!StringUtils.hasText(rawPhoneNumber)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "휴대폰번호를 입력해주세요.");
        }
        String normalized = rawPhoneNumber.replaceAll("\\D", "");
        if (normalized.length() < 10 || normalized.length() > 11) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "휴대폰번호 형식이 올바르지 않습니다.");
        }
        return normalized;
    }

    private @Nullable LocalDateTime parseExpiry(@Nullable String absolute, @Nullable String relativeSeconds) {
        if (StringUtils.hasText(absolute)) {
            try {
                return LocalDateTime.parse(absolute.trim(), KIS_EXPIRY_FORMAT);
            } catch (DateTimeParseException ignored) {
                // Fall through to relative seconds.
            }
        }
        if (StringUtils.hasText(relativeSeconds)) {
            try {
                return LocalDateTime.now().plusSeconds(Long.parseLong(relativeSeconds.trim()));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private @Nullable String decodeValue(@Nullable String raw) {
        if (!StringUtils.hasText(raw)) {
            return raw;
        }
        try {
            return URLDecoder.decode(raw, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            return raw;
        }
    }

    private @Nullable String firstNonBlank(@Nullable String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private @Nullable String maskAccount(@Nullable String accountNumber, @Nullable String accountProductCode) {
        if (!StringUtils.hasText(accountNumber)) {
            return null;
        }

        String sanitized = accountNumber.replaceAll("\\s+", "");
        String suffix = sanitized.length() <= 4 ? sanitized : sanitized.substring(sanitized.length() - 4);
        return "*".repeat(Math.max(0, sanitized.length() - suffix.length())) + suffix
                + (StringUtils.hasText(accountProductCode) ? "-" + accountProductCode : "");
    }

    private record KisConnectionStatePayload(
            Long userId,
            String customerName,
            String phoneNumber,
            String nonce,
            LocalDateTime issuedAt
    ) {
    }

    private record KisOAuthTokenResponse(
            @Nullable String access_token,
            @Nullable String refresh_token,
            @Nullable String access_token_token_expired,
            @Nullable String refresh_token_token_expired,
            @Nullable String expires_in,
            @Nullable String refresh_token_expires_in
    ) {
        public @Nullable String accessToken() {
            return access_token;
        }

        public @Nullable String refreshToken() {
            return refresh_token;
        }

        public @Nullable String accessTokenExpiresAt() {
            return access_token_token_expired;
        }

        public @Nullable String refreshTokenExpiresAt() {
            return refresh_token_token_expired;
        }

        public @Nullable String accessTokenExpiresIn() {
            return expires_in;
        }

        public @Nullable String refreshTokenExpiresIn() {
            return refresh_token_expires_in;
        }
    }
}
