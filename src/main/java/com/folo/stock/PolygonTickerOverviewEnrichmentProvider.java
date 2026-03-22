package com.folo.stock;

import com.folo.common.enums.MarketType;
import com.folo.config.MarketDataSyncProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

@Component
public class PolygonTickerOverviewEnrichmentProvider implements StockMetadataEnrichmentProvider {

    private static final String SOURCE_PAYLOAD_VERSION = "polygon:v3/reference/tickers";

    private final RestClient restClient;
    private final MarketDataSyncProperties properties;

    public PolygonTickerOverviewEnrichmentProvider(
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
        return properties.polygon().metadataEnabled() && StringUtils.hasText(properties.polygon().apiKey());
    }

    @Override
    public boolean supports(MarketType market) {
        return market == MarketType.NASDAQ
                || market == MarketType.NYSE
                || market == MarketType.AMEX;
    }

    @Override
    public StockMetadataEnrichmentRecord fetchMetadata(StockSymbol stockSymbol) {
        URI requestUri = UriComponentsBuilder.fromUriString(properties.polygon().baseUrl())
                .path("/v3/reference/tickers/{ticker}")
                .queryParam("apiKey", properties.polygon().apiKey())
                .buildAndExpand(stockSymbol.getTicker())
                .encode()
                .toUri();

        PolygonTickerOverviewResponse response = restClient.get()
                .uri(requestUri)
                .retrieve()
                .body(PolygonTickerOverviewResponse.class);

        PolygonTickerOverviewResult result = response == null ? null : response.results();
        if (result == null) {
            return new StockMetadataEnrichmentRecord(null, null, StockClassificationScheme.SIC, SOURCE_PAYLOAD_VERSION);
        }

        String industryNameRaw = normalize(result.sic_description());
        String sectorNameRaw = mapSicDivision(result.sic_code());

        return new StockMetadataEnrichmentRecord(
                sectorNameRaw,
                industryNameRaw,
                StockClassificationScheme.SIC,
                SOURCE_PAYLOAD_VERSION
        );
    }

    private String mapSicDivision(String sicCode) {
        if (!StringUtils.hasText(sicCode)) {
            return null;
        }

        String trimmed = sicCode.trim();
        if (trimmed.length() < 2) {
            return null;
        }

        try {
            int division = Integer.parseInt(trimmed.substring(0, 2));
            if (division <= 9) {
                return "Agriculture, Forestry and Fishing";
            }
            if (division <= 14) {
                return "Mining";
            }
            if (division <= 17) {
                return "Construction";
            }
            if (division <= 39) {
                return "Manufacturing";
            }
            if (division <= 49) {
                return "Transportation, Communications and Utilities";
            }
            if (division <= 51) {
                return "Wholesale Trade";
            }
            if (division <= 59) {
                return "Retail Trade";
            }
            if (division <= 67) {
                return "Finance, Insurance and Real Estate";
            }
            if (division <= 89) {
                return "Services";
            }
            if (division <= 99) {
                return "Public Administration";
            }
            return null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String normalize(String raw) {
        return StringUtils.hasText(raw) ? raw.trim() : null;
    }

    private record PolygonTickerOverviewResponse(
            PolygonTickerOverviewResult results
    ) {
    }

    private record PolygonTickerOverviewResult(
            String sic_code,
            String sic_description
    ) {
    }
}
