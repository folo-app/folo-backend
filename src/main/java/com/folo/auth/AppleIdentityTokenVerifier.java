package com.folo.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.folo.common.enums.AuthProvider;
import com.folo.common.exception.ApiException;
import com.folo.common.exception.ErrorCode;
import com.folo.config.AppleSocialAuthProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.Set;

@Component
@ConditionalOnProperty(prefix = "app.auth.social.apple", name = "enabled", havingValue = "true")
public class AppleIdentityTokenVerifier {

    private final RestClient restClient;
    private final AppleSocialAuthProperties properties;
    private final ObjectMapper objectMapper;

    public AppleIdentityTokenVerifier(
            RestClient.Builder restClientBuilder,
            AppleSocialAuthProperties properties,
            ObjectMapper objectMapper
    ) {
        this.restClient = restClientBuilder.build();
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public SocialProviderIdentity verify(AppleNativeAuthRequest request) {
        JsonNode header = decodeSegment(request.identityToken(), 0);
        String keyId = text(header, "kid");
        PublicKey publicKey = findApplePublicKey(keyId);

        Claims claims;
        try {
            claims = Jwts.parser().verifyWith((RSAPublicKey) publicKey).build()
                    .parseSignedClaims(request.identityToken())
                    .getPayload();
        } catch (JwtException | IllegalArgumentException exception) {
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS, "Apple identity token 검증에 실패했습니다.");
        }

        String subject = claims.getSubject();
        if (subject == null || subject.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Apple identity token에 sub 값이 없습니다.");
        }
        if (!properties.issuer().equals(claims.getIssuer())) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Apple issuer가 일치하지 않습니다.");
        }
        Set<String> audiences = claims.getAudience();
        if (audiences == null || audiences.stream().noneMatch(properties.audiences()::contains)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Apple audience가 일치하지 않습니다.");
        }
        if (request.userIdentifier() != null && !request.userIdentifier().isBlank() && !request.userIdentifier().equals(subject)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Apple 사용자 식별자가 토큰과 일치하지 않습니다.");
        }
        String nonce = claims.get("nonce", String.class);
        if (request.nonce() != null && !request.nonce().isBlank() && !request.nonce().equals(nonce)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Apple nonce가 일치하지 않습니다.");
        }

        String email = firstNonBlank(claims.get("email", String.class), request.email());
        boolean emailVerified = booleanClaim(claims.get("email_verified"));
        String nicknameSuggestion = buildNickname(request.givenName(), request.familyName(), email);
        return new SocialProviderIdentity(AuthProvider.APPLE, subject, email, emailVerified, nicknameSuggestion, null);
    }

    private PublicKey findApplePublicKey(String keyId) {
        JsonNode jwkSet = restClient.get()
                .uri(properties.jwkSetUrl())
                .retrieve()
                .body(JsonNode.class);
        JsonNode keys = jwkSet != null ? jwkSet.get("keys") : null;
        if (keys == null || !keys.isArray()) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "Apple 공개 키를 불러오지 못했습니다.");
        }

        for (JsonNode key : keys) {
            if (keyId.equals(nullableText(key, "kid"))) {
                return toPublicKey(key);
            }
        }
        throw new ApiException(ErrorCode.INTERNAL_ERROR, "Apple 공개 키 식별자와 일치하는 키가 없습니다.");
    }

    private PublicKey toPublicKey(JsonNode key) {
        try {
            BigInteger modulus = new BigInteger(1, Base64.getUrlDecoder().decode(text(key, "n")));
            BigInteger exponent = new BigInteger(1, Base64.getUrlDecoder().decode(text(key, "e")));
            return KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(modulus, exponent));
        } catch (Exception exception) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "Apple 공개 키 변환에 실패했습니다.");
        }
    }

    private JsonNode decodeSegment(String jwt, int index) {
        String[] parts = jwt.split("\\.");
        if (parts.length <= index) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Apple identity token 형식이 올바르지 않습니다.");
        }
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(parts[index]);
            return objectMapper.readTree(new String(decoded, StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Apple identity token 디코딩에 실패했습니다.");
        }
    }

    private static String text(JsonNode node, String field) {
        String value = nullableText(node, field);
        if (value == null || value.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Apple 응답에 " + field + " 값이 없습니다.");
        }
        return value;
    }

    private static @Nullable String nullableText(JsonNode node, String field) {
        JsonNode value = node != null ? node.get(field) : null;
        return value != null && !value.isNull() ? value.asText() : null;
    }

    private static boolean booleanClaim(@Nullable Object value) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof String stringValue) {
            return Boolean.parseBoolean(stringValue);
        }
        return false;
    }

    private static @Nullable String buildNickname(@Nullable String givenName, @Nullable String familyName, @Nullable String email) {
        String fullName = firstNonBlank(joinNames(familyName, givenName), givenName, familyName);
        if (fullName != null && !fullName.isBlank()) {
            return fullName;
        }
        if (email != null && email.contains("@")) {
            return email.substring(0, email.indexOf('@'));
        }
        return null;
    }

    private static @Nullable String joinNames(@Nullable String familyName, @Nullable String givenName) {
        String joined = ((familyName != null ? familyName : "") + (givenName != null ? givenName : "")).trim();
        return joined.isBlank() ? null : joined;
    }

    private static @Nullable String firstNonBlank(@Nullable String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
