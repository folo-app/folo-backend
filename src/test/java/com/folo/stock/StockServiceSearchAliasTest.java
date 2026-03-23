package com.folo.stock;

import com.folo.common.enums.AssetType;
import com.folo.common.enums.MarketType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockServiceSearchAliasTest {

    @Mock
    private StockSymbolRepository stockSymbolRepository;

    @Mock
    private PriceSnapshotRepository priceSnapshotRepository;

    @Mock
    private StockBrandingService stockBrandingService;

    @Mock
    private KisQuoteService kisQuoteService;

    @Mock
    private StockSymbolEnrichmentRepository stockSymbolEnrichmentRepository;

    @Mock
    private StockRecommendationService stockRecommendationService;

    @InjectMocks
    private StockService stockService;

    @Test
    void searchReturnsMetaWhenQueriedWithKoreanAlias() {
        StockSymbol meta = stockSymbol(91L, "META", "Meta Platforms, Inc.");

        when(stockSymbolRepository.findActiveByMarketsAndTickers(
                eq(List.of(MarketType.NASDAQ)),
                eq(List.of("META"))
        )).thenReturn(List.of(meta));
        when(stockSymbolRepository.searchTopByMarkets(
                eq(List.of(MarketType.NASDAQ)),
                eq("메타"),
                any(Pageable.class)
        )).thenReturn(List.of());
        when(priceSnapshotRepository.findAllByStockSymbolIdIn(List.of(91L))).thenReturn(List.of());
        when(kisQuoteService.fetchQuotes(List.of(meta))).thenReturn(Map.of());
        when(stockBrandingService.getPublicLogoUrl(meta)).thenReturn("https://cdn.example.com/meta.png");

        StockSearchResponse response = stockService.search("메타", "NASDAQ");

        assertThat(response.stocks()).hasSize(1);
        assertThat(response.stocks().get(0).ticker()).isEqualTo("META");
        assertThat(response.stocks().get(0).name()).isEqualTo("Meta Platforms, Inc.");
    }

    private StockSymbol stockSymbol(Long id, String ticker, String name) {
        StockSymbol stockSymbol = new StockSymbol();
        stockSymbol.setId(id);
        stockSymbol.setTicker(ticker);
        stockSymbol.setName(name);
        stockSymbol.setMarket(MarketType.NASDAQ);
        stockSymbol.setAssetType(AssetType.STOCK);
        stockSymbol.setActive(true);
        stockSymbol.setPrimaryExchangeCode("XNAS");
        stockSymbol.setCurrencyCode("USD");
        stockSymbol.setSectorName("Communication Services");
        stockSymbol.setSourceProvider(StockDataProvider.POLYGON);
        stockSymbol.setSourceIdentifier(ticker);
        stockSymbol.setLastMasterSyncedAt(LocalDateTime.now());
        return stockSymbol;
    }
}
