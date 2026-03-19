package com.folo.stock;

import com.folo.config.MarketDataSyncProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

class StockMasterStartupSyncRunnerTest {

    @Test
    void startupSyncFailureDoesNotAbortApplicationReadyFlow() {
        StockMasterSyncService syncService = mock(StockMasterSyncService.class);
        doThrow(new IllegalStateException("boom")).when(syncService).syncAll();

        StockMasterStartupSyncRunner runner = new StockMasterStartupSyncRunner(
                properties(true, true),
                syncService
        );

        assertDoesNotThrow(runner::syncOnStartup);
    }

    private MarketDataSyncProperties properties(boolean enabled, boolean runOnStartup) {
        return new MarketDataSyncProperties(
                enabled,
                runOnStartup,
                "0 0 4 * * *",
                "Asia/Seoul",
                500,
                new MarketDataSyncProperties.TwelveData(false, "", "https://api.twelvedata.com"),
                new MarketDataSyncProperties.Polygon(false, "", "https://api.polygon.io"),
                new MarketDataSyncProperties.Kis(false, "", "", "", "", "")
        );
    }
}
