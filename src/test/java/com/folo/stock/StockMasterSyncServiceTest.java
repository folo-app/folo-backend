package com.folo.stock;

import com.folo.common.enums.AssetType;
import com.folo.common.enums.MarketType;
import com.folo.config.MarketDataSyncProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StockMasterSyncServiceTest {

    @Test
    void syncMarketPreservesExistingEnrichmentFieldsWhenMasterHasFallbackValues() {
        StockMasterSyncProvider provider = mock(StockMasterSyncProvider.class);
        StockSymbolRepository stockSymbolRepository = mock(StockSymbolRepository.class);
        StockSymbolSyncRunRepository syncRunRepository = mock(StockSymbolSyncRunRepository.class);

        when(provider.provider()).thenReturn(StockDataProvider.KIS);
        when(provider.isConfigured()).thenReturn(true);
        when(provider.fetchBatch(eq(MarketType.KRX), eq(null), eq(500)))
                .thenReturn(new StockMasterSyncBatch(List.of(masterRecord()), null));

        StockSymbol existing = new StockSymbol();
        existing.setMarket(MarketType.KRX);
        existing.setTicker("005930");
        existing.setSectorName("Technology");
        existing.setAnnualDividendYield(new BigDecimal("2.2500"));
        existing.setDividendMonthsCsv("3,6,9,12");

        when(stockSymbolRepository.findByMarketAndTicker(MarketType.KRX, "005930"))
                .thenReturn(Optional.of(existing));
        when(stockSymbolRepository.findAllBySourceProviderAndMarket(StockDataProvider.KIS, MarketType.KRX))
                .thenReturn(List.of());
        when(stockSymbolRepository.save(any(StockSymbol.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(syncRunRepository.save(any(StockSymbolSyncRun.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StockMasterSyncService service = new StockMasterSyncService(
                properties(),
                List.of(provider),
                stockSymbolRepository,
                syncRunRepository
        );

        service.syncMarket(provider, MarketType.KRX);

        ArgumentCaptor<StockSymbol> captor = ArgumentCaptor.forClass(StockSymbol.class);
        verify(stockSymbolRepository).save(captor.capture());
        StockSymbol saved = captor.getValue();
        assertEquals("Technology", saved.getSectorName());
        assertEquals(new BigDecimal("2.2500"), saved.getAnnualDividendYield());
        assertEquals("3,6,9,12", saved.getDividendMonthsCsv());
        assertEquals("삼성전자", saved.getName());
        assertEquals("KRW", saved.getCurrencyCode());
    }

    @Test
    void syncMarketSeedsMasterMetadataWhenSymbolDoesNotHaveThemYet() {
        StockMasterSyncProvider provider = mock(StockMasterSyncProvider.class);
        StockSymbolRepository stockSymbolRepository = mock(StockSymbolRepository.class);
        StockSymbolSyncRunRepository syncRunRepository = mock(StockSymbolSyncRunRepository.class);

        when(provider.provider()).thenReturn(StockDataProvider.KIS);
        when(provider.isConfigured()).thenReturn(true);
        when(provider.fetchBatch(eq(MarketType.KRX), eq(null), eq(500)))
                .thenReturn(new StockMasterSyncBatch(List.of(masterRecord()), null));

        when(stockSymbolRepository.findByMarketAndTicker(MarketType.KRX, "005930"))
                .thenReturn(Optional.empty());
        when(stockSymbolRepository.findAllBySourceProviderAndMarket(StockDataProvider.KIS, MarketType.KRX))
                .thenReturn(List.of());
        when(stockSymbolRepository.save(any(StockSymbol.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(syncRunRepository.save(any(StockSymbolSyncRun.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StockMasterSyncService service = new StockMasterSyncService(
                properties(),
                List.of(provider),
                stockSymbolRepository,
                syncRunRepository
        );

        service.syncMarket(provider, MarketType.KRX);

        ArgumentCaptor<StockSymbol> captor = ArgumentCaptor.forClass(StockSymbol.class);
        verify(stockSymbolRepository, times(1)).save(captor.capture());
        StockSymbol saved = captor.getValue();
        assertEquals("Semiconductors", saved.getSectorName());
        assertEquals(new BigDecimal("2.1500"), saved.getAnnualDividendYield());
        assertEquals("3,6,9,12", saved.getDividendMonthsCsv());
        assertNull(saved.getId());
    }

    private StockMasterSymbolRecord masterRecord() {
        return new StockMasterSymbolRecord(
                MarketType.KRX,
                "005930",
                "삼성전자",
                AssetType.STOCK,
                true,
                "XKRX",
                "KRW",
                "005930",
                "Semiconductors",
                new BigDecimal("2.1500"),
                "3,6,9,12"
        );
    }

    private MarketDataSyncProperties properties() {
        return new MarketDataSyncProperties(
                true,
                false,
                "0 0 4 * * *",
                "0 30 4 * * *",
                "0 0 5 * * *",
                "Asia/Seoul",
                500,
                new MarketDataSyncProperties.TwelveData(false, "", "https://api.twelvedata.com"),
                new MarketDataSyncProperties.Polygon(false, false, false, "", "https://api.polygon.io"),
                new MarketDataSyncProperties.Kis(true, false, "", "", "", "", "")
        );
    }
}
