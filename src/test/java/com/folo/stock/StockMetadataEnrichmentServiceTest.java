package com.folo.stock;

import com.folo.common.enums.AssetType;
import com.folo.common.enums.MarketType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class StockMetadataEnrichmentServiceTest {

    @Autowired
    private StockMetadataEnrichmentService stockMetadataEnrichmentService;

    @Autowired
    private StockSymbolRepository stockSymbolRepository;

    @Autowired
    private StockSymbolEnrichmentRepository stockSymbolEnrichmentRepository;

    @Autowired
    private StockSymbolSyncRunRepository stockSymbolSyncRunRepository;

    @MockBean
    private PolygonTickerOverviewEnrichmentProvider polygonTickerOverviewEnrichmentProvider;

    @Test
    void syncSymbolsUpsertsMetadataAndUpdatesRepresentativeSector() {
        StockSymbol stockSymbol = createStockSymbol("META201");

        given(polygonTickerOverviewEnrichmentProvider.provider()).willReturn(StockDataProvider.POLYGON);
        given(polygonTickerOverviewEnrichmentProvider.isConfigured()).willReturn(true);
        given(polygonTickerOverviewEnrichmentProvider.supports(MarketType.NASDAQ)).willReturn(true);
        given(polygonTickerOverviewEnrichmentProvider.supports(MarketType.NYSE)).willReturn(true);
        given(polygonTickerOverviewEnrichmentProvider.supports(MarketType.AMEX)).willReturn(true);
        given(polygonTickerOverviewEnrichmentProvider.supports(MarketType.KRX)).willReturn(false);
        given(polygonTickerOverviewEnrichmentProvider.fetchMetadata(eq(stockSymbol))).willReturn(
                new StockMetadataEnrichmentRecord(
                        "Manufacturing",
                        "SEMICONDUCTORS AND RELATED DEVICES",
                        StockClassificationScheme.SIC,
                        "polygon:v3/reference/tickers"
                )
        );

        stockMetadataEnrichmentService.syncSymbols(List.of(stockSymbol.getId()));

        StockSymbol refreshed = stockSymbolRepository.findById(stockSymbol.getId()).orElseThrow();
        StockSymbolEnrichment enrichment = stockSymbolEnrichmentRepository
                .findByStockSymbolIdAndProvider(stockSymbol.getId(), StockDataProvider.POLYGON)
                .orElseThrow();

        assertThat(refreshed.getSectorName()).isEqualTo("Manufacturing");
        assertThat(enrichment.getIndustryNameRaw()).isEqualTo("SEMICONDUCTORS AND RELATED DEVICES");
        assertThat(enrichment.getClassificationScheme()).isEqualTo(StockClassificationScheme.SIC);
        assertThat(stockSymbolSyncRunRepository.findAll()).hasSize(1);
        assertThat(stockSymbolSyncRunRepository.findAll().get(0).getSyncScope()).isEqualTo(StockSymbolSyncScope.ENRICHMENT);
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
}
