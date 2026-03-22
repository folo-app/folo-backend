package com.folo.stock;

import com.folo.common.enums.AssetType;
import com.folo.common.enums.MarketType;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DividendAnalyticsServiceTest {

    @Autowired
    private DividendAnalyticsService dividendAnalyticsService;

    @Autowired
    private StockSymbolRepository stockSymbolRepository;

    @Autowired
    private StockDividendEventRepository stockDividendEventRepository;

    @Autowired
    private PriceSnapshotRepository priceSnapshotRepository;

    @Test
    void refreshAnalyticsCalculatesTrailingYieldFromRecentCashDividends() {
        StockSymbol stockSymbol = createStockSymbol("DIVYLD1");
        createPriceSnapshot(stockSymbol, BigDecimal.valueOf(100));
        LocalDate today = LocalDate.now();

        createDividendEvent(stockSymbol, "yield-1", DividendEventType.CASH, today.minusMonths(11), BigDecimal.ONE);
        createDividendEvent(stockSymbol, "yield-2", DividendEventType.CASH, today.minusMonths(8), BigDecimal.ONE);
        createDividendEvent(stockSymbol, "yield-3", DividendEventType.CASH, today.minusMonths(5), BigDecimal.ONE);
        createDividendEvent(stockSymbol, "yield-4", DividendEventType.CASH, today.minusMonths(2), BigDecimal.ONE);

        dividendAnalyticsService.refreshAnalytics(stockSymbol.getId());

        StockSymbol refreshed = stockSymbolRepository.findById(stockSymbol.getId()).orElseThrow();
        assertThat(refreshed.getAnnualDividendYield()).isEqualByComparingTo("4.0000");
    }

    @Test
    void refreshAnalyticsCalculatesRecurringDividendMonthsFromTwoYearsOfEvents() {
        StockSymbol stockSymbol = createStockSymbol("DIVMON1");

        createDividendEvent(stockSymbol, "month-1", DividendEventType.CASH, LocalDate.of(LocalDate.now().getYear() - 1, 3, 15), BigDecimal.ONE);
        createDividendEvent(stockSymbol, "month-2", DividendEventType.CASH, LocalDate.of(LocalDate.now().getYear() - 1, 6, 15), BigDecimal.ONE);
        createDividendEvent(stockSymbol, "month-3", DividendEventType.CASH, LocalDate.of(LocalDate.now().getYear() - 1, 9, 15), BigDecimal.ONE);
        createDividendEvent(stockSymbol, "month-4", DividendEventType.CASH, LocalDate.of(LocalDate.now().getYear() - 1, 12, 15), BigDecimal.ONE);
        createDividendEvent(stockSymbol, "month-5", DividendEventType.CASH, LocalDate.of(LocalDate.now().getYear() - 2, 3, 15), BigDecimal.ONE);
        createDividendEvent(stockSymbol, "month-6", DividendEventType.CASH, LocalDate.of(LocalDate.now().getYear() - 2, 6, 15), BigDecimal.ONE);
        createDividendEvent(stockSymbol, "month-7", DividendEventType.CASH, LocalDate.of(LocalDate.now().getYear() - 2, 9, 15), BigDecimal.ONE);
        createDividendEvent(stockSymbol, "month-8", DividendEventType.CASH, LocalDate.of(LocalDate.now().getYear() - 2, 12, 15), BigDecimal.ONE);

        dividendAnalyticsService.refreshAnalytics(stockSymbol.getId());

        StockSymbol refreshed = stockSymbolRepository.findById(stockSymbol.getId()).orElseThrow();
        assertThat(refreshed.getDividendMonthsCsv()).isEqualTo("3,6,9,12");
    }

    @Test
    void refreshAnalyticsLeavesYieldNullWhenPriceSnapshotIsMissing() {
        StockSymbol stockSymbol = createStockSymbol("DIVNUL1");
        createDividendEvent(stockSymbol, "null-1", DividendEventType.CASH, LocalDate.now().minusMonths(1), BigDecimal.ONE);
        createDividendEvent(stockSymbol, "null-2", DividendEventType.CASH, LocalDate.now().minusMonths(4), BigDecimal.ONE);

        dividendAnalyticsService.refreshAnalytics(stockSymbol.getId());

        StockSymbol refreshed = stockSymbolRepository.findById(stockSymbol.getId()).orElseThrow();
        assertThat(refreshed.getAnnualDividendYield()).isNull();
    }

    @Test
    void refreshAnalyticsLeavesDividendMonthsNullForIrregularOneOffMonths() {
        StockSymbol stockSymbol = createStockSymbol("DIVIRR1");

        int previousYear = LocalDate.now().getYear() - 1;
        createDividendEvent(stockSymbol, "irr-1", DividendEventType.CASH, LocalDate.of(previousYear, 2, 15), BigDecimal.ONE);
        createDividendEvent(stockSymbol, "irr-2", DividendEventType.CASH, LocalDate.of(previousYear, 5, 15), BigDecimal.ONE);
        createDividendEvent(stockSymbol, "irr-3", DividendEventType.CASH, LocalDate.of(previousYear, 8, 15), BigDecimal.ONE);

        dividendAnalyticsService.refreshAnalytics(stockSymbol.getId());

        StockSymbol refreshed = stockSymbolRepository.findById(stockSymbol.getId()).orElseThrow();
        assertThat(refreshed.getDividendMonthsCsv()).isNull();
    }

    private StockSymbol createStockSymbol(String ticker) {
        StockSymbol stockSymbol = new StockSymbol();
        stockSymbol.setMarket(MarketType.NASDAQ);
        stockSymbol.setTicker(ticker);
        stockSymbol.setName("Dividend Test " + ticker);
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

    private void createDividendEvent(
            StockSymbol stockSymbol,
            String sourceEventId,
            DividendEventType eventType,
            LocalDate payDate,
            BigDecimal cashAmount
    ) {
        StockDividendEvent event = new StockDividendEvent();
        event.setStockSymbol(stockSymbol);
        event.setProvider(StockDataProvider.POLYGON);
        event.setSourceEventId(sourceEventId);
        event.setDedupeKey(sourceEventId + "-dedupe");
        event.setEventType(eventType);
        event.setPayDate(payDate);
        event.setCashAmount(cashAmount);
        event.setCurrencyCode("USD");
        stockDividendEventRepository.save(event);
    }
}
