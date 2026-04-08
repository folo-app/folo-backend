package com.folo.portfolio;

import com.folo.common.enums.AssetType;
import com.folo.common.enums.CurrencyCode;
import com.folo.common.enums.MarketType;
import com.folo.fx.FxRateService;
import com.folo.stock.PriceSnapshot;
import com.folo.stock.PriceSnapshotRepository;
import com.folo.stock.StockSectorCode;
import com.folo.stock.StockSymbol;
import com.folo.stock.StockSymbolEnrichmentRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PortfolioValuationServiceTest {

    @Test
    void valuateConvertsMixedCurrencyHoldingsIntoDisplayCurrencyWeights() {
        PriceSnapshotRepository priceSnapshotRepository = mock(PriceSnapshotRepository.class);
        StockSymbolEnrichmentRepository enrichmentRepository = mock(StockSymbolEnrichmentRepository.class);
        FxRateService fxRateService = mock(FxRateService.class);
        PortfolioValuationService service = new PortfolioValuationService(
                priceSnapshotRepository,
                enrichmentRepository,
                fxRateService
        );

        Holding appleHolding = mock(Holding.class);
        StockSymbol apple = mock(StockSymbol.class);
        PriceSnapshot appleSnapshot = mock(PriceSnapshot.class);
        when(appleHolding.getId()).thenReturn(101L);
        when(appleHolding.getStockSymbol()).thenReturn(apple);
        when(appleHolding.getQuantity()).thenReturn(new BigDecimal("2"));
        when(appleHolding.getAvgPrice()).thenReturn(new BigDecimal("150"));
        when(appleHolding.getTotalInvested()).thenReturn(new BigDecimal("300"));
        when(apple.getId()).thenReturn(1L);
        when(apple.getTicker()).thenReturn("AAPL");
        when(apple.getName()).thenReturn("Apple");
        when(apple.getMarket()).thenReturn(MarketType.NASDAQ);
        when(apple.getAssetType()).thenReturn(AssetType.STOCK);
        when(apple.getCurrencyCode()).thenReturn("USD");
        when(apple.getSectorCode()).thenReturn(StockSectorCode.INFORMATION_TECHNOLOGY);
        when(apple.getSectorName()).thenReturn(StockSectorCode.INFORMATION_TECHNOLOGY.label());
        when(apple.getAnnualDividendYield()).thenReturn(new BigDecimal("1.0000"));
        when(apple.getDividendMonthsCsv()).thenReturn("3,6,9,12");
        when(appleSnapshot.getStockSymbol()).thenReturn(apple);
        when(appleSnapshot.getCurrentPrice()).thenReturn(new BigDecimal("200"));
        when(appleSnapshot.getDayReturn()).thenReturn(new BigDecimal("10"));

        Holding bankHolding = mock(Holding.class);
        StockSymbol bank = mock(StockSymbol.class);
        PriceSnapshot bankSnapshot = mock(PriceSnapshot.class);
        when(bankHolding.getId()).thenReturn(102L);
        when(bankHolding.getStockSymbol()).thenReturn(bank);
        when(bankHolding.getQuantity()).thenReturn(new BigDecimal("10"));
        when(bankHolding.getAvgPrice()).thenReturn(new BigDecimal("100000"));
        when(bankHolding.getTotalInvested()).thenReturn(new BigDecimal("1000000"));
        when(bank.getId()).thenReturn(2L);
        when(bank.getTicker()).thenReturn("KB");
        when(bank.getName()).thenReturn("KB금융");
        when(bank.getMarket()).thenReturn(MarketType.KRX);
        when(bank.getAssetType()).thenReturn(AssetType.STOCK);
        when(bank.getCurrencyCode()).thenReturn("KRW");
        when(bank.getSectorCode()).thenReturn(StockSectorCode.FINANCIALS);
        when(bank.getSectorName()).thenReturn(StockSectorCode.FINANCIALS.label());
        when(bank.getAnnualDividendYield()).thenReturn(new BigDecimal("0.0000"));
        when(bank.getDividendMonthsCsv()).thenReturn(null);
        when(bankSnapshot.getStockSymbol()).thenReturn(bank);
        when(bankSnapshot.getCurrentPrice()).thenReturn(new BigDecimal("120000"));
        when(bankSnapshot.getDayReturn()).thenReturn(new BigDecimal("5000"));

        when(priceSnapshotRepository.findAllByStockSymbolIdIn(List.of(1L, 2L)))
                .thenReturn(List.of(appleSnapshot, bankSnapshot));
        when(enrichmentRepository.findLatestCandidatesByStockSymbolIds(List.of(1L, 2L)))
                .thenReturn(List.of());

        LocalDateTime fxAsOf = LocalDateTime.of(2026, 4, 5, 6, 0);
        when(fxRateService.convert(any(BigDecimal.class), eq(CurrencyCode.USD), eq(CurrencyCode.KRW)))
                .thenAnswer(invocation -> new FxRateService.ConversionResult(
                        ((BigDecimal) invocation.getArgument(0))
                                .multiply(new BigDecimal("1300"))
                                .setScale(4, RoundingMode.HALF_UP),
                        fxAsOf,
                        false
                ));
        when(fxRateService.convert(any(BigDecimal.class), eq(CurrencyCode.KRW), eq(CurrencyCode.KRW)))
                .thenAnswer(invocation -> new FxRateService.ConversionResult(
                        ((BigDecimal) invocation.getArgument(0)).setScale(4, RoundingMode.HALF_UP),
                        null,
                        false
                ));

        PortfolioValuationService.PortfolioValuationResult result = service.valuate(
                List.of(appleHolding, bankHolding),
                CurrencyCode.KRW
        );

        assertThat(result.displayCurrency()).isEqualTo(CurrencyCode.KRW);
        assertThat(result.fxAsOf()).isEqualTo(fxAsOf);
        assertThat(result.totalInvested()).isEqualByComparingTo("1390000.0000");
        assertThat(result.totalValue()).isEqualByComparingTo("1720000.0000");
        assertThat(result.totalReturn()).isEqualByComparingTo("330000.0000");
        assertThat(result.dayReturn()).isEqualByComparingTo("76000.0000");

        PortfolioHoldingItem appleItem = result.holdings().stream()
                .filter(item -> item.ticker().equals("AAPL"))
                .findFirst()
                .orElseThrow();
        PortfolioHoldingItem bankItem = result.holdings().stream()
                .filter(item -> item.ticker().equals("KB"))
                .findFirst()
                .orElseThrow();

        assertThat(appleItem.displayTotalValue()).isEqualByComparingTo("520000.0000");
        assertThat(appleItem.weight()).isEqualByComparingTo("30.2326");
        assertThat(appleItem.sectorName()).isEqualTo("정보기술");
        assertThat(bankItem.displayTotalValue()).isEqualByComparingTo("1200000.0000");
        assertThat(bankItem.weight()).isEqualByComparingTo("69.7674");
        assertThat(bankItem.sectorName()).isEqualTo("금융");

        assertThat(result.sectorAllocations())
                .extracting(PortfolioAllocationItem::key, PortfolioAllocationItem::label)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("financials", "금융"),
                        org.assertj.core.groups.Tuple.tuple("information_technology", "정보기술")
                );
    }
}
