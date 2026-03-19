package com.folo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "integration.market-data")
public record MarketDataSyncProperties(
        boolean enabled,
        String masterCron,
        String zone,
        int batchSize,
        Polygon polygon,
        Kis kis
) {
    public record Polygon(
            boolean enabled,
            String apiKey,
            String baseUrl
    ) {
    }

    public record Kis(
            boolean enabled,
            String masterFileUrl
    ) {
    }
}
