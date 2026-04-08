package com.folo.stock;

import com.folo.config.MarketDataSyncProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

class StockSectorStartupBackfillRunnerTest {

    @Test
    void startupBackfillFailureDoesNotAbortApplicationReadyFlow() {
        StockSectorBackfillService backfillService = mock(StockSectorBackfillService.class);
        doThrow(new IllegalStateException("boom")).when(backfillService).backfillActiveSymbols(500);

        StockSectorStartupBackfillRunner runner = new StockSectorStartupBackfillRunner(
                properties(true, true),
                backfillService
        );

        assertDoesNotThrow(runner::backfillOnStartup);
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
