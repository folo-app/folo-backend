package com.folo.stock;

import com.folo.config.MarketDataSyncProperties;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Service
public class KisAccessTokenService {

    private static final DateTimeFormatter KIS_EXPIRY_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final RestClient restClient;
    private final MarketDataSyncProperties properties;

    private volatile CachedToken cachedToken;

    public KisAccessTokenService(
            RestClient.Builder restClientBuilder,
            MarketDataSyncProperties properties
    ) {
        this.restClient = restClientBuilder.build();
        this.properties = properties;
    }

    public boolean isConfigured() {
        return properties.kis().enabled()
                && StringUtils.hasText(properties.kis().baseUrl())
                && StringUtils.hasText(properties.kis().appKey())
                && StringUtils.hasText(properties.kis().appSecret());
    }

    public String getAccessToken() {
        if (!isConfigured()) {
            throw new IllegalStateException("KIS market data configuration is incomplete.");
        }

        CachedToken current = cachedToken;
        Instant now = Instant.now();
        if (current != null && current.expiresAt().isAfter(now.plusSeconds(60))) {
            return current.accessToken();
        }

        synchronized (this) {
            current = cachedToken;
            now = Instant.now();
            if (current != null && current.expiresAt().isAfter(now.plusSeconds(60))) {
                return current.accessToken();
            }

            CachedToken refreshed = requestNewToken();
            cachedToken = refreshed;
            return refreshed.accessToken();
        }
    }

    private CachedToken requestNewToken() {
        URI requestUri = UriComponentsBuilder.fromUriString(properties.kis().baseUrl())
                .path("/oauth2/tokenP")
                .build(true)
                .toUri();

        KisTokenResponse response = restClient.post()
                .uri(requestUri)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "grant_type", "client_credentials",
                        "appkey", properties.kis().appKey(),
                        "appsecret", properties.kis().appSecret()
                ))
                .retrieve()
                .body(KisTokenResponse.class);

        if (response == null || !StringUtils.hasText(response.access_token())) {
            throw new IllegalStateException("KIS access token response is empty.");
        }

        return new CachedToken(
                response.access_token(),
                resolveExpiry(response)
        );
    }

    private Instant resolveExpiry(KisTokenResponse response) {
        if (StringUtils.hasText(response.access_token_token_expired())) {
            LocalDateTime expiresAt = LocalDateTime.parse(
                    response.access_token_token_expired().trim(),
                    KIS_EXPIRY_FORMAT
            );
            return expiresAt.atZone(ZoneId.systemDefault()).toInstant();
        }

        if (StringUtils.hasText(response.expires_in())) {
            try {
                return Instant.now().plusSeconds(Long.parseLong(response.expires_in().trim()));
            } catch (NumberFormatException ignored) {
                // Fall through to default TTL.
            }
        }

        return Instant.now().plusSeconds(60 * 60 * 12);
    }

    private record CachedToken(String accessToken, Instant expiresAt) {
    }

    private record KisTokenResponse(
            String access_token,
            String access_token_token_expired,
            String expires_in
    ) {
    }
}
