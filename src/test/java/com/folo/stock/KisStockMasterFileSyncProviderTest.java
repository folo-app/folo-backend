package com.folo.stock;

import com.folo.common.enums.MarketType;
import com.folo.config.MarketDataSyncProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KisStockMasterFileSyncProviderTest {

    @Test
    void supportsReturnsFalseWhenConfiguredLocalMasterFileDoesNotExist() {
        KisStockMasterFileSyncProvider provider = new KisStockMasterFileSyncProvider(
                properties("/path/that/does/not/exist.csv", "")
        );

        assertFalse(provider.supports(MarketType.KRX));
    }

    @Test
    void supportsReturnsTrueForRemoteMasterSource() {
        KisStockMasterFileSyncProvider provider = new KisStockMasterFileSyncProvider(
                properties("https://example.com/kis-domestic-master.csv", "")
        );

        assertTrue(provider.supports(MarketType.KRX));
    }

    private MarketDataSyncProperties properties(String domesticMasterFileUrl, String overseasMasterFileUrl) {
        return new MarketDataSyncProperties(
                true,
                true,
                "0 0 4 * * *",
                "0 30 4 * * *",
                "0 0 5 * * *",
                "Asia/Seoul",
                500,
                new MarketDataSyncProperties.TwelveData(false, "", "https://api.twelvedata.com"),
                new MarketDataSyncProperties.Polygon(false, false, false, "", "https://api.polygon.io"),
                new MarketDataSyncProperties.Kis(true, false, "", "", "", domesticMasterFileUrl, overseasMasterFileUrl)
        );
    }
}
