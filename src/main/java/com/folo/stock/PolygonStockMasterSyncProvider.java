package com.folo.stock;

import com.folo.common.enums.AssetType;
import com.folo.common.enums.MarketType;
import com.folo.config.MarketDataSyncProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

@Component
public class PolygonStockMasterSyncProvider implements StockMasterSyncProvider {

    private final RestClient restClient;
    private final MarketDataSyncProperties properties;

    public PolygonStockMasterSyncProvider(
            RestClient.Builder restClientBuilder,
            MarketDataSyncProperties properties
    ) {
        this.restClient = restClientBuilder.build();
        this.properties = properties;
    }

    @Override
    public StockDataProvider provider() {
        return StockDataProvider.POLYGON;
    }

    @Override
    public boolean isConfigured() {
        return false;
    }

    @Override
    public boolean supports(MarketType market) {
        return false;
    }

    @Override
    public StockMasterSyncBatch fetchBatch(MarketType market, String cursor, int batchSize) {
        URI requestUri = buildRequestUri(cursor, batchSize);
        PolygonReferenceTickersResponse response = restClient.get()
                .uri(requestUri)
                .retrieve()
                .body(PolygonReferenceTickersResponse.class);

        if (response == null || response.results() == null) {
            return new StockMasterSyncBatch(List.of(), null);
        }

        List<StockMasterSymbolRecord> records = new ArrayList<>();

        for (PolygonTickerResult result : response.results()) {
            MarketType resolvedMarket = resolveMarket(result.primary_exchange());

            if (resolvedMarket != market || !StringUtils.hasText(result.ticker()) || !StringUtils.hasText(result.name())) {
                continue;
            }

            records.add(new StockMasterSymbolRecord(
                    resolvedMarket,
                    result.ticker(),
                    result.name(),
                    resolveAssetType(result.type()),
                    result.active() == null || result.active(),
                    result.primary_exchange(),
                    StringUtils.hasText(result.currency_symbol()) ? result.currency_symbol() : "USD",
                    StringUtils.hasText(result.composite_figi()) ? result.composite_figi() : result.ticker(),
                    null,
                    null,
                    null
            ));
        }

        return new StockMasterSyncBatch(records, response.next_url());
    }

    private URI buildRequestUri(String cursor, int batchSize) {
        UriComponentsBuilder builder = StringUtils.hasText(cursor)
                ? UriComponentsBuilder.fromUriString(cursor)
                : UriComponentsBuilder.fromUriString(properties.polygon().baseUrl())
                .path("/v3/reference/tickers")
                .queryParam("market", "stocks")
                .queryParam("active", "true")
                .queryParam("order", "asc")
                .queryParam("sort", "ticker")
                .queryParam("limit", Math.min(batchSize, 1000));

        return builder
                .replaceQueryParam("apiKey", properties.polygon().apiKey())
                .build(true)
                .toUri();
    }

    private MarketType resolveMarket(String primaryExchange) {
        if (!StringUtils.hasText(primaryExchange)) {
            return null;
        }

        return switch (primaryExchange.toUpperCase()) {
            case "XNAS" -> MarketType.NASDAQ;
            case "XNYS" -> MarketType.NYSE;
            case "XASE" -> MarketType.AMEX;
            default -> null;
        };
    }

    private AssetType resolveAssetType(String type) {
        if (!StringUtils.hasText(type)) {
            return AssetType.STOCK;
        }

        return type.toUpperCase().contains("ETF") ? AssetType.ETF : AssetType.STOCK;
    }

    private record PolygonReferenceTickersResponse(
            List<PolygonTickerResult> results,
            String next_url
    ) {
    }

    private record PolygonTickerResult(
            String ticker,
            String name,
            String type,
            Boolean active,
            String primary_exchange,
            String currency_symbol,
            String composite_figi
    ) {
    }
}
