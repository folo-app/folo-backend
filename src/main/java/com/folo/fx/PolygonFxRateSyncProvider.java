package com.folo.fx;

import com.folo.common.enums.CurrencyCode;
import com.folo.config.MarketDataSyncProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Component
public class PolygonFxRateSyncProvider implements FxRateSyncProvider {

    private final RestClient restClient;
    private final MarketDataSyncProperties properties;

    public PolygonFxRateSyncProvider(
            RestClient.Builder restClientBuilder,
            MarketDataSyncProperties properties
    ) {
        this.restClient = restClientBuilder.build();
        this.properties = properties;
    }

    @Override
    public FxRateProvider provider() {
        return FxRateProvider.POLYGON;
    }

    @Override
    public boolean isConfigured() {
        return properties.enabled()
                && properties.polygon() != null
                && StringUtils.hasText(properties.polygon().apiKey())
                && StringUtils.hasText(properties.polygon().baseUrl());
    }

    @Override
    public FxRateSnapshot fetchRate(CurrencyCode baseCurrency, CurrencyCode quoteCurrency) {
        URI requestUri = UriComponentsBuilder.fromUriString(properties.polygon().baseUrl())
                .path("/v1/conversion/{base}/{quote}")
                .queryParam("amount", 1)
                .queryParam("precision", 8)
                .queryParam("apiKey", properties.polygon().apiKey())
                .buildAndExpand(baseCurrency.name(), quoteCurrency.name())
                .encode()
                .toUri();

        PolygonFxConversionResponse response = restClient.get()
                .uri(requestUri)
                .retrieve()
                .body(PolygonFxConversionResponse.class);

        if (response == null || response.converted() == null || response.converted().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("Polygon FX conversion response is empty");
        }

        LocalDateTime asOf = response.last() != null && response.last().timestamp() != null
                ? LocalDateTime.ofInstant(Instant.ofEpochMilli(response.last().timestamp()), ZoneOffset.UTC)
                : LocalDateTime.now(ZoneOffset.UTC);

        return new FxRateSnapshot(
                baseCurrency,
                quoteCurrency,
                response.converted(),
                asOf,
                provider()
        );
    }

    private record PolygonFxConversionResponse(
            BigDecimal converted,
            PolygonFxLast last
    ) {
    }

    private record PolygonFxLast(
            Long timestamp
    ) {
    }
}
