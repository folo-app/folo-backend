package com.folo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "integration.market-data")
public record MarketDataSyncProperties(
        boolean enabled,
        boolean runOnStartup,
        String masterCron,
        String dividendCron,
        String metadataCron,
        String zone,
        int batchSize,
        TwelveData twelveData,
        Polygon polygon,
        Kis kis
) {
    public record TwelveData(
            boolean logoEnabled,
            String apiKey,
            String baseUrl
    ) {
    }

    public record Polygon(
            boolean logoEnabled,
            boolean dividendEnabled,
            boolean metadataEnabled,
            String apiKey,
            String baseUrl
    ) {
    }

    public record Kis(
            boolean enabled,
            boolean dividendEnabled,
            String baseUrl,
            String appKey,
            String appSecret,
            String domesticMasterFileUrl,
            String overseasMasterFileUrl
    ) {
    }
}
