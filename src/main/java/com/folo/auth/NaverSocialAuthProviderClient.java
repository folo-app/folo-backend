package com.folo.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.folo.common.enums.AuthProvider;
import com.folo.common.exception.ApiException;
import com.folo.common.exception.ErrorCode;
import com.folo.config.NaverSocialAuthProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

@Component
@ConditionalOnProperty(prefix = "app.auth.social.naver", name = "enabled", havingValue = "true")
public class NaverSocialAuthProviderClient implements SocialAuthProviderClient {

    private final RestClient restClient;
    private final NaverSocialAuthProperties properties;

    public NaverSocialAuthProviderClient(RestClient.Builder restClientBuilder, NaverSocialAuthProperties properties) {
        this.restClient = restClientBuilder.build();
        this.properties = properties;
    }

    @Override
    public AuthProvider provider() {
        return AuthProvider.NAVER;
    }

    @Override
    public SocialAuthStartResult start(SocialAuthStartCommand command) {
        String authorizationUrl = UriComponentsBuilder.fromUriString(properties.authorizationUrl())
                .queryParam("response_type", "code")
                .queryParam("client_id", properties.clientId())
                .queryParam("redirect_uri", properties.redirectUri())
                .queryParam("state", command.state())
                .queryParam("scope", properties.scope())
                .encode()
                .build()
                .toUriString();
        return new SocialAuthStartResult(authorizationUrl, null, null);
    }

    @Override
    public SocialProviderIdentity completeAuthorization(SocialAuthorizationState state, Map<String, String> callbackParams) {
        String error = callbackParams.get("error");
        if (error != null && !error.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "네이버 로그인 실패: " + error);
        }
        String code = callbackParams.get("code");
        String callbackState = callbackParams.get("state");
        if (code == null || code.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "네이버 authorization code가 없습니다.");
        }
        if (callbackState == null || callbackState.isBlank()) {
            throw new ApiException(ErrorCode.INVALID_SOCIAL_AUTH_STATE, "네이버 state가 없습니다.");
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", properties.clientId());
        form.add("client_secret", properties.clientSecret());
        form.add("code", code);
        form.add("state", callbackState);

        JsonNode tokenResponse = restClient.post()
                .uri(properties.tokenUrl())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(JsonNode.class);
        String accessToken = text(tokenResponse, "access_token");

        JsonNode profileEnvelope = restClient.get()
                .uri(properties.userInfoUrl())
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .body(JsonNode.class);
        JsonNode profile = profileEnvelope.get("response");

        String providerUserId = text(profile, "id");
        String email = nullableText(profile, "email");
        boolean emailVerified = email != null && !email.isBlank();
        String nicknameSuggestion = firstNonBlank(nullableText(profile, "nickname"), firstNonBlank(nullableText(profile, "name"), deriveNickname(email)));
        String profileImage = nullableText(profile, "profile_image");
        return new SocialProviderIdentity(AuthProvider.NAVER, providerUserId, email, emailVerified, nicknameSuggestion, profileImage);
    }

    private static String text(JsonNode node, String field) {
        String value = nullableText(node, field);
        if (value == null || value.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "네이버 응답에 " + field + " 값이 없습니다.");
        }
        return value;
    }

    private static String nullableText(JsonNode node, String field) {
        JsonNode value = node != null ? node.get(field) : null;
        return value != null && !value.isNull() ? value.asText() : null;
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
