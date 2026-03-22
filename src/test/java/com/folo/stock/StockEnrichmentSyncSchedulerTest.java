package com.folo.stock;

import com.folo.config.MarketDataSyncProperties;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class StockEnrichmentSyncSchedulerTest {

    @Test
    void schedulerSkipsWhenMarketDataIntegrationIsDisabled() {
        StockDividendEnrichmentService dividendService = mock(StockDividendEnrichmentService.class);
        StockMetadataEnrichmentService metadataService = mock(StockMetadataEnrichmentService.class);
        StockEnrichmentSyncScheduler scheduler = new StockEnrichmentSyncScheduler(
                properties(false, false),
                dividendService,
                metadataService
        );

        scheduler.syncDividendNightly();
        scheduler.syncMetadataNightly();

        verify(dividendService, never()).syncPrioritySymbols();
        verify(metadataService, never()).syncPrioritySymbols();
    }

    private MarketDataSyncProperties properties(boolean enabled, boolean runOnStartup) {
        return new MarketDataSyncProperties(
                enabled,
                runOnStartup,
                "0 0 4 * * *",
                "0 30 4 * * *",
                "0 0 5 * * *",
                "Asia/Seoul",
                500,
                new MarketDataSyncProperties.TwelveData(false, "", "https://api.twelvedata.com"),
                new MarketDataSyncProperties.Polygon(false, false, false, "", "https://api.polygon.io"),
                new MarketDataSyncProperties.Kis(false, false, "", "", "", "", "")
        );
    }
}
