package com.folo.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.folo.common.enums.AuthProvider;
import com.folo.common.exception.ApiException;
import com.folo.common.exception.ErrorCode;
import com.folo.config.GoogleSocialAuthProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

@Component
@ConditionalOnProperty(prefix = "app.auth.social.google", name = "enabled", havingValue = "true")
public class GoogleSocialAuthProviderClient implements SocialAuthProviderClient {

    private final RestClient restClient;
    private final GoogleSocialAuthProperties properties;

    public GoogleSocialAuthProviderClient(RestClient.Builder restClientBuilder, GoogleSocialAuthProperties properties) {
        this.restClient = restClientBuilder.build();
        this.properties = properties;
    }

    @Override
    public AuthProvider provider() {
        return AuthProvider.GOOGLE;
    }

    @Override
    public SocialAuthStartResult start(SocialAuthStartCommand command) {
        String authorizationUrl = UriComponentsBuilder.fromUriString(properties.authorizationUrl())
                .queryParam("client_id", properties.clientId())
                .queryParam("redirect_uri", properties.redirectUri())
                .queryParam("response_type", "code")
                .queryParam("scope", properties.scope())
                .queryParam("access_type", "offline")
                .queryParam("include_granted_scopes", "true")
                .queryParam("prompt", "select_account")
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
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Google 로그인 실패: " + error);
        }
        String code = callbackParams.get("code");
        if (code == null || code.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Google authorization code가 없습니다.");
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("code", code);
        form.add("client_id", properties.clientId());
        form.add("client_secret", properties.clientSecret());
        form.add("redirect_uri", properties.redirectUri());
        form.add("grant_type", "authorization_code");

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

        String providerUserId = text(userInfo, "sub");
        String email = nullableText(userInfo, "email");
        boolean emailVerified = bool(userInfo, "email_verified");
        String nicknameSuggestion = firstNonBlank(nullableText(userInfo, "name"), deriveNickname(email));
        String profileImage = nullableText(userInfo, "picture");
        return new SocialProviderIdentity(AuthProvider.GOOGLE, providerUserId, email, emailVerified, nicknameSuggestion, profileImage);
    }

    private static String text(JsonNode node, String field) {
        String value = nullableText(node, field);
        if (value == null || value.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Google 응답에 " + field + " 값이 없습니다.");
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
