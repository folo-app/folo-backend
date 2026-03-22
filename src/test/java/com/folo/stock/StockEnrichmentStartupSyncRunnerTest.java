package com.folo.stock;

import com.folo.config.MarketDataSyncProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

class StockEnrichmentStartupSyncRunnerTest {

    @Test
    void startupSyncFailureDoesNotAbortApplicationReadyFlow() {
        StockDividendEnrichmentService dividendService = mock(StockDividendEnrichmentService.class);
        StockMetadataEnrichmentService metadataService = mock(StockMetadataEnrichmentService.class);
        doThrow(new IllegalStateException("metadata boom")).when(metadataService).syncPrioritySymbols();
        doThrow(new IllegalStateException("dividend boom")).when(dividendService).syncPrioritySymbols();

        StockEnrichmentStartupSyncRunner runner = new StockEnrichmentStartupSyncRunner(
                properties(true, true),
                dividendService,
                metadataService
        );

        assertDoesNotThrow(runner::syncOnStartup);
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
