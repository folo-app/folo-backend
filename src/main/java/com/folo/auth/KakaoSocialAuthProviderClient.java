package com.folo.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.folo.common.enums.AuthProvider;
import com.folo.common.exception.ApiException;
import com.folo.common.exception.ErrorCode;
import com.folo.config.KakaoSocialAuthProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

@Component
@ConditionalOnProperty(prefix = "app.auth.social.kakao", name = "enabled", havingValue = "true")
public class KakaoSocialAuthProviderClient implements SocialAuthProviderClient {

    private final RestClient restClient;
    private final KakaoSocialAuthProperties properties;

    public KakaoSocialAuthProviderClient(RestClient.Builder restClientBuilder, KakaoSocialAuthProperties properties) {
        this.restClient = restClientBuilder.build();
        this.properties = properties;
    }

    @Override
    public AuthProvider provider() {
        return AuthProvider.KAKAO;
    }

    @Override
    public SocialAuthStartResult start(SocialAuthStartCommand command) {
        String authorizationUrl = UriComponentsBuilder.fromUriString(properties.authorizationUrl())
                .queryParam("client_id", properties.clientId())
                .queryParam("redirect_uri", properties.redirectUri())
                .queryParam("response_type", "code")
                .queryParam("scope", properties.scope())
                .queryParam("state", command.state())
                .encode()
                .build()
                .toUriString();
        return new SocialAuthStartResult(authorizationUrl, null, null);
    }

    @Override
    public SocialProviderIdentity completeAuthorization(SocialAuthorizationState state, Map<String, String> callbackParams) {
        String error = callbackParams.get("error");
        if (error != null && !error.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "카카오 로그인 실패: " + error);
        }
        String code = callbackParams.get("code");
        if (code == null || code.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "카카오 authorization code가 없습니다.");
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", properties.clientId());
        if (properties.clientSecret() != null && !properties.clientSecret().isBlank()) {
            form.add("client_secret", properties.clientSecret());
        }
        form.add("redirect_uri", properties.redirectUri());
        form.add("code", code);

        JsonNode tokenResponse = restClient.post()
                .uri(properties.tokenUrl())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(JsonNode.class);
        String accessToken = text(tokenResponse, "access_token");

        JsonNode userInfo = restClient.get()
                .uri(properties.userInfoUrl())
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .body(JsonNode.class);

        String providerUserId = text(userInfo, "id");
        JsonNode kakaoAccount = userInfo.get("kakao_account");
        JsonNode profile = kakaoAccount != null ? kakaoAccount.get("profile") : null;
        String email = nullableText(kakaoAccount, "email");
        boolean emailVerified = bool(kakaoAccount, "is_email_verified") && bool(kakaoAccount, "is_email_valid");
        String nicknameSuggestion = firstNonBlank(nullableText(profile, "nickname"), deriveNickname(email));
        String profileImage = firstNonBlank(nullableText(profile, "profile_image_url"), nullableText(profile, "thumbnail_image_url"));
        return new SocialProviderIdentity(AuthProvider.KAKAO, providerUserId, email, emailVerified, nicknameSuggestion, profileImage);
    }

    private static String text(JsonNode node, String field) {
        String value = nullableText(node, field);
        if (value == null || value.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "카카오 응답에 " + field + " 값이 없습니다.");
        }
        return value;
    }

    private static String nullableText(JsonNode node, String field) {
        JsonNode value = node != null ? node.get(field) : null;
        return value != null && !value.isNull() ? value.asText() : null;
    }

    private static boolean bool(JsonNode node, String field) {
        JsonNode value = node != null ? node.get(field) : null;
        return value != null && !value.isNull() && value.asBoolean(false);
    }

    private static String deriveNickname(String email) {
        if (email == null || email.isBlank() || !email.contains("@")) {
            return null;
        }
        return email.substring(0, email.indexOf('@'));
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second;
    }
}
