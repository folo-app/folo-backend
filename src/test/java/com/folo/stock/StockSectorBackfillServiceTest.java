package com.folo.stock;

import com.folo.common.enums.AssetType;
import com.folo.common.enums.MarketType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

class StockSectorBackfillServiceTest {

    @Test
    void backfillActiveSymbolsCanonicalizesStocksAndEtfs() {
        StockSymbolRepository stockSymbolRepository = mock(StockSymbolRepository.class);
        StockSymbolEnrichmentRepository enrichmentRepository = mock(StockSymbolEnrichmentRepository.class);
        KrxDomesticSectorMapService sectorMapService = mock(KrxDomesticSectorMapService.class);

        StockSymbol samsung = symbol(1L, "005930", "삼성전자", AssetType.STOCK, null, null);
        StockSymbol apple = symbol(2L, "AAPL", "Apple", AssetType.STOCK, null, "Technology");
        StockSymbol etf = symbol(3L, "360750", "TIGER 미국S&P500", AssetType.ETF, null, null);

        when(stockSymbolRepository.findAllByActiveTrueOrderByIdAsc()).thenReturn(List.of(samsung, apple, etf));
        when(stockSymbolRepository.save(any(StockSymbol.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(enrichmentRepository.findLatestCandidatesByStockSymbolIds(List.of(1L, 2L, 3L)))
                .thenReturn(List.of(
                        enrichment(samsung, "Technology", "Semiconductors", StockClassificationScheme.KIS_MASTER),
                        enrichment(apple, null, "SERVICES-PREPACKAGED SOFTWARE", StockClassificationScheme.SIC)
                ));
        when(sectorMapService.resolve(any(StockSymbol.class))).thenReturn(null);

        StockSectorBackfillService service = new StockSectorBackfillService(
                stockSymbolRepository,
                enrichmentRepository,
                sectorMapService
        );

        StockSectorBackfillService.BackfillResult result = service.backfillActiveSymbols(500);

        assertThat(result.processedCount()).isEqualTo(3);
        assertThat(result.updatedCount()).isEqualTo(3);

        ArgumentCaptor<StockSymbol> captor = ArgumentCaptor.forClass(StockSymbol.class);
        verify(stockSymbolRepository, times(3)).save(captor.capture());
        List<StockSymbol> saved = captor.getAllValues();

        assertThat(saved).anySatisfy(symbol -> {
            assertThat(symbol.getTicker()).isEqualTo("005930");
            assertThat(symbol.getSectorCode()).isEqualTo(StockSectorCode.INFORMATION_TECHNOLOGY);
            assertThat(symbol.getSectorName()).isEqualTo("정보기술");
        });
        assertThat(saved).anySatisfy(symbol -> {
            assertThat(symbol.getTicker()).isEqualTo("AAPL");
            assertThat(symbol.getSectorCode()).isEqualTo(StockSectorCode.INFORMATION_TECHNOLOGY);
            assertThat(symbol.getSectorName()).isEqualTo("정보기술");
        });
        assertThat(saved).anySatisfy(symbol -> {
            assertThat(symbol.getTicker()).isEqualTo("360750");
            assertThat(symbol.getSectorCode()).isNull();
            assertThat(symbol.getSectorName()).isEqualTo("ETF");
        });
    }

    @Test
    void backfillActiveSymbolsFallsBackToOtherWhenNoMetadataExists() {
        StockSymbolRepository stockSymbolRepository = mock(StockSymbolRepository.class);
        StockSymbolEnrichmentRepository enrichmentRepository = mock(StockSymbolEnrichmentRepository.class);
        KrxDomesticSectorMapService sectorMapService = mock(KrxDomesticSectorMapService.class);

        StockSymbol stock = symbol(1L, "000040", "KR모터스", AssetType.STOCK, null, null);

        when(stockSymbolRepository.findAllByActiveTrueOrderByIdAsc()).thenReturn(List.of(stock));
        when(stockSymbolRepository.save(any(StockSymbol.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(enrichmentRepository.findLatestCandidatesByStockSymbolIds(List.of(1L))).thenReturn(List.of());
        when(sectorMapService.resolve(any(StockSymbol.class))).thenReturn(null);

        StockSectorBackfillService service = new StockSectorBackfillService(
                stockSymbolRepository,
                enrichmentRepository,
                sectorMapService
        );

        StockSectorBackfillService.BackfillResult result = service.backfillActiveSymbols(100);

        assertThat(result.processedCount()).isEqualTo(1);
        assertThat(result.updatedCount()).isEqualTo(1);
        assertThat(stock.getSectorCode()).isEqualTo(StockSectorCode.OTHER);
        assertThat(stock.getSectorName()).isEqualTo("기타");
    }

    @Test
    void backfillActiveSymbolsUpgradesOtherToMappedKrxSector() {
        StockSymbolRepository stockSymbolRepository = mock(StockSymbolRepository.class);
        StockSymbolEnrichmentRepository enrichmentRepository = mock(StockSymbolEnrichmentRepository.class);
        KrxDomesticSectorMapService sectorMapService = mock(KrxDomesticSectorMapService.class);

        StockSymbol samsung = symbol(1L, "005930", "삼성전자", AssetType.STOCK, StockSectorCode.OTHER, "기타");

        when(stockSymbolRepository.findAllByActiveTrueOrderByIdAsc()).thenReturn(List.of(samsung));
        when(stockSymbolRepository.save(any(StockSymbol.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(enrichmentRepository.findLatestCandidatesByStockSymbolIds(List.of(1L))).thenReturn(List.of());
        when(sectorMapService.resolve(samsung)).thenReturn(new StockMetadataEnrichmentRecord(
                "Technology",
                "Semiconductors",
                StockClassificationScheme.KRX_SECTOR_MAP,
                "krx:sector-map:v1"
        ));

        StockSectorBackfillService service = new StockSectorBackfillService(
                stockSymbolRepository,
                enrichmentRepository,
                sectorMapService
        );

        StockSectorBackfillService.BackfillResult result = service.backfillActiveSymbols(100);

        assertThat(result.updatedCount()).isEqualTo(1);
        assertThat(samsung.getSectorCode()).isEqualTo(StockSectorCode.INFORMATION_TECHNOLOGY);
        assertThat(samsung.getSectorName()).isEqualTo("정보기술");
    }

    @Test
    void backfillActiveSymbolsAllowsExplicitTickerOverrideToCorrectConcreteSector() {
        StockSymbolRepository stockSymbolRepository = mock(StockSymbolRepository.class);
        StockSymbolEnrichmentRepository enrichmentRepository = mock(StockSymbolEnrichmentRepository.class);
        KrxDomesticSectorMapService sectorMapService = mock(KrxDomesticSectorMapService.class);

        StockSymbol ubiquoss = symbol(
                1L,
                "078070",
                "유비쿼스홀딩스",
                AssetType.STOCK,
                StockSectorCode.COMMUNICATION_SERVICES,
                "커뮤니케이션 서비스"
        );

        when(stockSymbolRepository.findAllByActiveTrueOrderByIdAsc()).thenReturn(List.of(ubiquoss));
        when(stockSymbolRepository.save(any(StockSymbol.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(enrichmentRepository.findLatestCandidatesByStockSymbolIds(List.of(1L))).thenReturn(List.of());
        when(sectorMapService.resolve(ubiquoss)).thenReturn(new StockMetadataEnrichmentRecord(
                "Information Technology",
                "Communications Equipment",
                StockClassificationScheme.KRX_SECTOR_MAP,
                "krx:sector-map:v5"
        ));

        StockSectorBackfillService service = new StockSectorBackfillService(
                stockSymbolRepository,
                enrichmentRepository,
                sectorMapService
        );

        StockSectorBackfillService.BackfillResult result = service.backfillActiveSymbols(100);

        assertThat(result.updatedCount()).isEqualTo(1);
        assertThat(ubiquoss.getSectorCode()).isEqualTo(StockSectorCode.INFORMATION_TECHNOLOGY);
        assertThat(ubiquoss.getSectorName()).isEqualTo("정보기술");
    }

    private StockSymbol symbol(
            Long id,
            String ticker,
            String name,
            AssetType assetType,
            StockSectorCode sectorCode,
            String sectorName
    ) {
        StockSymbol symbol = new StockSymbol();
        symbol.setId(id);
        symbol.setTicker(ticker);
        symbol.setName(name);
        symbol.setMarket(MarketType.KRX);
        symbol.setAssetType(assetType);
        symbol.setActive(true);
        symbol.setSectorCode(sectorCode);
        symbol.setSectorName(sectorName);
        return symbol;
    }

    private StockSymbolEnrichment enrichment(
            StockSymbol stockSymbol,
            String sectorNameRaw,
            String industryNameRaw,
            StockClassificationScheme classificationScheme
    ) {
        StockSymbolEnrichment enrichment = new StockSymbolEnrichment();
        enrichment.setStockSymbol(stockSymbol);
        enrichment.setProvider(StockDataProvider.KIS);
        enrichment.setSectorNameRaw(sectorNameRaw);
        enrichment.setIndustryNameRaw(industryNameRaw);
        enrichment.setClassificationScheme(classificationScheme);
        enrichment.setLastEnrichedAt(LocalDateTime.now());
        return enrichment;
    }
}
