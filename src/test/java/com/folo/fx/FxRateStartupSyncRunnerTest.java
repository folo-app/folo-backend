package com.folo.fx;

import com.folo.config.MarketDataSyncProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

class FxRateStartupSyncRunnerTest {

    @Test
    void startupSyncFailureDoesNotAbortApplicationReadyFlow() {
        FxRateService fxRateService = mock(FxRateService.class);
        doThrow(new IllegalStateException("boom")).when(fxRateService).syncUsdKrw();

        FxRateStartupSyncRunner runner = new FxRateStartupSyncRunner(
                properties(true, true),
                fxRateService
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
