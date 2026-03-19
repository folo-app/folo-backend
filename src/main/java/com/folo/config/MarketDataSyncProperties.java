package com.folo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "integration.market-data")
public record MarketDataSyncProperties(
        boolean enabled,
        boolean runOnStartup,
        String masterCron,
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
            String apiKey,
            String baseUrl
    ) {
    }

    public record Kis(
            boolean enabled,
            String baseUrl,
            String appKey,
            String appSecret,
            String domesticMasterFileUrl,
            String overseasMasterFileUrl
    ) {
    }
}
