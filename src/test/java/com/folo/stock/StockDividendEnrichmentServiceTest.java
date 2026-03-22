package com.folo.stock;

import com.folo.common.enums.AssetType;
import com.folo.common.enums.MarketType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class StockDividendEnrichmentServiceTest {

    @Autowired
    private StockDividendEnrichmentService stockDividendEnrichmentService;

    @Autowired
    private StockSymbolRepository stockSymbolRepository;

    @Autowired
    private StockDividendEventRepository stockDividendEventRepository;

    @Autowired
    private PriceSnapshotRepository priceSnapshotRepository;

    @Autowired
    private StockSymbolSyncRunRepository stockSymbolSyncRunRepository;

    @MockBean
    private PolygonDividendSyncProvider polygonDividendSyncProvider;

    @Test
    void syncSymbolsUpsertsEventsRefreshesAnalyticsAndRecordsSyncRun() {
        StockSymbol stockSymbol = createStockSymbol("DIVA201");
        createPriceSnapshot(stockSymbol, BigDecimal.valueOf(100));

        given(polygonDividendSyncProvider.provider()).willReturn(StockDataProvider.POLYGON);
        given(polygonDividendSyncProvider.isConfigured()).willReturn(true);
        given(polygonDividendSyncProvider.supports(MarketType.NASDAQ)).willReturn(true);
        given(polygonDividendSyncProvider.supports(MarketType.NYSE)).willReturn(true);
        given(polygonDividendSyncProvider.supports(MarketType.AMEX)).willReturn(true);
        given(polygonDividendSyncProvider.supports(MarketType.KRX)).willReturn(false);
        given(polygonDividendSyncProvider.fetchEvents(eq(stockSymbol), any(LocalDate.class))).willReturn(List.of(
                new DividendEventRecord(
                        StockDataProvider.POLYGON,
                        "evt-q1-current",
                        DividendEventType.CASH,
                        null,
                        LocalDate.of(LocalDate.now().getYear(), 3, 1),
                        null,
                        LocalDate.of(LocalDate.now().getYear(), 3, 15),
                        BigDecimal.ONE,
                        "USD",
                        "4"
                ),
                new DividendEventRecord(
                        StockDataProvider.POLYGON,
                        "evt-q2-current",
                        DividendEventType.CASH,
                        null,
                        LocalDate.of(LocalDate.now().getYear() - 1, 6, 1),
                        null,
                        LocalDate.of(LocalDate.now().getYear() - 1, 6, 15),
                        BigDecimal.ONE,
                        "USD",
                        "4"
                ),
                new DividendEventRecord(
                        StockDataProvider.POLYGON,
                        "evt-q3-current",
                        DividendEventType.CASH,
                        null,
                        LocalDate.of(LocalDate.now().getYear() - 1, 9, 1),
                        null,
                        LocalDate.of(LocalDate.now().getYear() - 1, 9, 15),
                        BigDecimal.ONE,
                        "USD",
                        "4"
                ),
                new DividendEventRecord(
                        StockDataProvider.POLYGON,
                        "evt-q4-current",
                        DividendEventType.CASH,
                        null,
                        LocalDate.of(LocalDate.now().getYear() - 1, 12, 1),
                        null,
                        LocalDate.of(LocalDate.now().getYear() - 1, 12, 15),
                        BigDecimal.ONE,
                        "USD",
                        "4"
                ),
                new DividendEventRecord(
                        StockDataProvider.POLYGON,
                        "evt-q1-prior",
                        DividendEventType.CASH,
                        null,
                        LocalDate.of(LocalDate.now().getYear() - 1, 3, 1),
                        null,
                        LocalDate.of(LocalDate.now().getYear() - 1, 3, 15),
                        BigDecimal.ONE,
                        "USD",
                        "4"
                ),
                new DividendEventRecord(
                        StockDataProvider.POLYGON,
                        "evt-q2-prior",
                        DividendEventType.CASH,
                        null,
                        LocalDate.of(LocalDate.now().getYear() - 2, 6, 1),
                        null,
                        LocalDate.of(LocalDate.now().getYear() - 2, 6, 15),
                        BigDecimal.ONE,
                        "USD",
                        "4"
                ),
                new DividendEventRecord(
                        StockDataProvider.POLYGON,
                        "evt-q3-prior",
                        DividendEventType.CASH,
                        null,
                        LocalDate.of(LocalDate.now().getYear() - 2, 9, 1),
                        null,
                        LocalDate.of(LocalDate.now().getYear() - 2, 9, 15),
                        BigDecimal.ONE,
                        "USD",
                        "4"
                ),
                new DividendEventRecord(
                        StockDataProvider.POLYGON,
                        "evt-q4-prior",
                        DividendEventType.CASH,
                        null,
                        LocalDate.of(LocalDate.now().getYear() - 2, 12, 1),
                        null,
                        LocalDate.of(LocalDate.now().getYear() - 2, 12, 15),
                        BigDecimal.ONE,
                        "USD",
                        "4"
                )
        ));

        stockDividendEnrichmentService.syncSymbols(List.of(stockSymbol.getId()));

        StockSymbol refreshed = stockSymbolRepository.findById(stockSymbol.getId()).orElseThrow();
        assertThat(stockDividendEventRepository.findAllByStockSymbolId(stockSymbol.getId())).hasSize(8);
        assertThat(refreshed.getAnnualDividendYield()).isEqualByComparingTo("4.0000");
        assertThat(refreshed.getDividendMonthsCsv()).isEqualTo("3,6,9,12");
        assertThat(stockSymbolSyncRunRepository.findAll()).hasSize(1);
        assertThat(stockSymbolSyncRunRepository.findAll().get(0).getSyncScope()).isEqualTo(StockSymbolSyncScope.DIVIDEND);
        assertThat(stockSymbolSyncRunRepository.findAll().get(0).getStatus()).isEqualTo(StockSymbolSyncStatus.COMPLETED);
    }

    private StockSymbol createStockSymbol(String ticker) {
        StockSymbol stockSymbol = new StockSymbol();
        stockSymbol.setMarket(MarketType.NASDAQ);
        stockSymbol.setTicker(ticker);
        stockSymbol.setName("Test " + ticker);
        stockSymbol.setAssetType(AssetType.STOCK);
        stockSymbol.setActive(true);
        stockSymbol.setPrimaryExchangeCode("XNAS");
        stockSymbol.setCurrencyCode("USD");
        stockSymbol.setSourceProvider(StockDataProvider.POLYGON);
        stockSymbol.setSourceIdentifier(ticker);
        stockSymbol.setLastMasterSyncedAt(LocalDateTime.now());
        return stockSymbolRepository.save(stockSymbol);
    }

    private void createPriceSnapshot(StockSymbol stockSymbol, BigDecimal currentPrice) {
        PriceSnapshot snapshot = new PriceSnapshot();
        snapshot.setStockSymbol(stockSymbol);
        snapshot.setCurrentPrice(currentPrice);
        snapshot.setOpenPrice(currentPrice);
        snapshot.setHighPrice(currentPrice);
        snapshot.setLowPrice(currentPrice);
        snapshot.setDayReturn(BigDecimal.ZERO);
        snapshot.setDayReturnRate(BigDecimal.ZERO);
        snapshot.setMarketUpdatedAt(LocalDateTime.now());
        priceSnapshotRepository.save(snapshot);
    }
}
