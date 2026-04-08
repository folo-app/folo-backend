package com.folo.stock;

import com.folo.common.enums.AssetType;
import com.folo.common.enums.MarketType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KrxDomesticSectorMapServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void resolvesTickerMappedSectorWhenCurrentSectorIsMissing() throws IOException {
        Path csv = tempDir.resolve("krx-sector-map.csv");
        Files.writeString(csv, """
                ticker,indutyCode,sectorName,industryName,sourcePayloadVersion
                005930,,Technology,Semiconductors,krx:sector-map:v1
                """);

        KrxPreferredStockResolver preferredStockResolver = mock(KrxPreferredStockResolver.class);
        StockIssuerProfileRepository issuerProfileRepository = mock(StockIssuerProfileRepository.class);
        KrxDomesticSectorMapService service = new KrxDomesticSectorMapService(
                csv.toString(),
                preferredStockResolver,
                issuerProfileRepository
        );

        StockMetadataEnrichmentRecord record = service.resolve(stockSymbol(1L, "005930", null, null));

        assertThat(service.isConfigured()).isTrue();
        assertThat(record).isNotNull();
        assertThat(record.classificationScheme()).isEqualTo(StockClassificationScheme.KRX_SECTOR_MAP);
        assertThat(record.sectorNameRaw()).isEqualTo("Technology");
        assertThat(record.industryNameRaw()).isEqualTo("Semiconductors");
    }

    @Test
    void resolvesIndustryCodeMappedSectorWhenTickerRowIsMissing() throws IOException {
        Path csv = tempDir.resolve("krx-sector-map.csv");
        Files.writeString(csv, """
                ticker,indutyCode,sectorName,industryName,sourcePayloadVersion
                ,26429,Technology,Semiconductors,krx:sector-map:v1
                """);

        KrxPreferredStockResolver preferredStockResolver = mock(KrxPreferredStockResolver.class);
        StockIssuerProfileRepository issuerProfileRepository = mock(StockIssuerProfileRepository.class);
        StockIssuerProfile profile = new StockIssuerProfile();
        profile.setIndutyCode("26429");
        when(issuerProfileRepository.findByStockSymbolIdAndProvider(1L, StockDataProvider.OPENDART))
                .thenReturn(Optional.of(profile));

        KrxDomesticSectorMapService service = new KrxDomesticSectorMapService(
                csv.toString(),
                preferredStockResolver,
                issuerProfileRepository
        );

        StockMetadataEnrichmentRecord record = service.resolve(stockSymbol(1L, "005930", StockSectorCode.OTHER, "기타"));

        assertThat(record).isNotNull();
        assertThat(record.sectorNameRaw()).isEqualTo("Technology");
        assertThat(record.industryNameRaw()).isEqualTo("Semiconductors");
    }

    @Test
    void returnsNullWhenConcreteSectorAlreadyExists() throws IOException {
        Path csv = tempDir.resolve("krx-sector-map.csv");
        Files.writeString(csv, """
                ticker,indutyCode,sectorName,industryName,sourcePayloadVersion
                001200,,Financials,Capital Markets,krx:sector-map:v1
                """);

        KrxPreferredStockResolver preferredStockResolver = mock(KrxPreferredStockResolver.class);
        StockIssuerProfileRepository issuerProfileRepository = mock(StockIssuerProfileRepository.class);
        KrxDomesticSectorMapService service = new KrxDomesticSectorMapService(
                csv.toString(),
                preferredStockResolver,
                issuerProfileRepository
        );

        StockMetadataEnrichmentRecord record = service.resolve(
                stockSymbol(1L, "005930", StockSectorCode.FINANCIALS, "금융")
        );

        assertThat(record).isNull();
    }

    @Test
    void explicitTickerRowOverridesExistingConcreteSector() throws IOException {
        Path csv = tempDir.resolve("krx-sector-map.csv");
        Files.writeString(csv, """
                ticker,indutyCode,sectorName,industryName,sourcePayloadVersion
                078070,,Information Technology,Communications Equipment,krx:sector-map:v1
                """);

        KrxPreferredStockResolver preferredStockResolver = mock(KrxPreferredStockResolver.class);
        StockIssuerProfileRepository issuerProfileRepository = mock(StockIssuerProfileRepository.class);
        KrxDomesticSectorMapService service = new KrxDomesticSectorMapService(
                csv.toString(),
                preferredStockResolver,
                issuerProfileRepository
        );

        StockMetadataEnrichmentRecord record = service.resolve(
                stockSymbol(1L, "078070", StockSectorCode.COMMUNICATION_SERVICES, "커뮤니케이션 서비스", "유비쿼스홀딩스")
        );

        assertThat(record).isNotNull();
        assertThat(record.sectorNameRaw()).isEqualTo("Information Technology");
        assertThat(record.industryNameRaw()).isEqualTo("Communications Equipment");
    }

    @Test
    void resolvesNameHeuristicWhenCsvAndIndustryCodeDoNotMatch() throws IOException {
        Path csv = tempDir.resolve("krx-sector-map.csv");
        Files.writeString(csv, """
                ticker,indutyCode,sectorName,industryName,sourcePayloadVersion
                005930,,Technology,Semiconductors,krx:sector-map:v1
                """);

        KrxPreferredStockResolver preferredStockResolver = mock(KrxPreferredStockResolver.class);
        StockIssuerProfileRepository issuerProfileRepository = mock(StockIssuerProfileRepository.class);
        KrxDomesticSectorMapService service = new KrxDomesticSectorMapService(
                csv.toString(),
                preferredStockResolver,
                issuerProfileRepository
        );

        StockMetadataEnrichmentRecord record = service.resolve(stockSymbol(1L, "035420", null, null, "NAVER"));

        assertThat(record).isNotNull();
        assertThat(record.sectorNameRaw()).isEqualTo("Communication Services");
        assertThat(record.industryNameRaw()).isEqualTo("Interactive Media & Services");
    }

    @Test
    void resolvesHeuristicWithoutCsvWhenNameSignalIsStrong() {
        KrxPreferredStockResolver preferredStockResolver = mock(KrxPreferredStockResolver.class);
        StockIssuerProfileRepository issuerProfileRepository = mock(StockIssuerProfileRepository.class);
        KrxDomesticSectorMapService service = new KrxDomesticSectorMapService(
                tempDir.resolve("missing.csv").toString(),
                preferredStockResolver,
                issuerProfileRepository
        );

        StockMetadataEnrichmentRecord record = service.resolve(stockSymbol(1L, "001200", null, null, "유진투자증권"));

        assertThat(record).isNotNull();
        assertThat(record.sectorNameRaw()).isEqualTo("Financials");
        assertThat(record.industryNameRaw()).isEqualTo("Capital Markets");
    }

    @Test
    void resolvesHoldingCompanyHeuristicWhenNameHasHoldingsSignal() {
        KrxPreferredStockResolver preferredStockResolver = mock(KrxPreferredStockResolver.class);
        StockIssuerProfileRepository issuerProfileRepository = mock(StockIssuerProfileRepository.class);
        KrxDomesticSectorMapService service = new KrxDomesticSectorMapService(
                tempDir.resolve("missing.csv").toString(),
                preferredStockResolver,
                issuerProfileRepository
        );

        StockMetadataEnrichmentRecord record = service.resolve(stockSymbol(1L, "004990", null, null, "롯데지주"));

        assertThat(record).isNotNull();
        assertThat(record.sectorNameRaw()).isEqualTo("Holding Companies");
        assertThat(record.industryNameRaw()).isEqualTo("Holding Companies");
    }

    @Test
    void resolvesInvestmentHeuristicWhenNameHasVentureCapitalSignal() {
        KrxPreferredStockResolver preferredStockResolver = mock(KrxPreferredStockResolver.class);
        StockIssuerProfileRepository issuerProfileRepository = mock(StockIssuerProfileRepository.class);
        KrxDomesticSectorMapService service = new KrxDomesticSectorMapService(
                tempDir.resolve("missing.csv").toString(),
                preferredStockResolver,
                issuerProfileRepository
        );

        StockMetadataEnrichmentRecord record = service.resolve(stockSymbol(1L, "041190", null, null, "우리기술투자"));

        assertThat(record).isNotNull();
        assertThat(record.sectorNameRaw()).isEqualTo("Financials");
        assertThat(record.industryNameRaw()).isEqualTo("Capital Markets");
    }

    @Test
    void resolvesPreferredShareByInheritingBaseCommonStockSector() throws IOException {
        Path csv = tempDir.resolve("krx-sector-map.csv");
        Files.writeString(csv, """
                ticker,indutyCode,sectorName,industryName,sourcePayloadVersion
                005930,,Technology,Semiconductors,krx:sector-map:v1
                """);

        KrxPreferredStockResolver preferredStockResolver = mock(KrxPreferredStockResolver.class);
        StockIssuerProfileRepository issuerProfileRepository = mock(StockIssuerProfileRepository.class);

        StockSymbol common = stockSymbol(10L, "005930", StockSectorCode.INFORMATION_TECHNOLOGY, "정보기술", "삼성전자");
        StockSymbol preferred = stockSymbol(11L, "005935", StockSectorCode.OTHER, "기타", "삼성전자우");
        when(preferredStockResolver.resolveBaseCommonStock(preferred)).thenReturn(common);

        KrxDomesticSectorMapService service = new KrxDomesticSectorMapService(
                csv.toString(),
                preferredStockResolver,
                issuerProfileRepository
        );

        StockMetadataEnrichmentRecord record = service.resolve(preferred);

        assertThat(record).isNotNull();
        assertThat(record.sectorNameRaw()).isEqualTo("Information Technology");
        assertThat(record.classificationScheme()).isEqualTo(StockClassificationScheme.KRX_SECTOR_MAP);
    }

    private StockSymbol stockSymbol(
            Long id,
            String ticker,
            StockSectorCode sectorCode,
            String sectorName
    ) {
        return stockSymbol(id, ticker, sectorCode, sectorName, "Sample " + ticker);
    }

    private StockSymbol stockSymbol(
            Long id,
            String ticker,
            StockSectorCode sectorCode,
            String sectorName,
            String name
    ) {
        StockSymbol stockSymbol = new StockSymbol();
        stockSymbol.setId(id);
        stockSymbol.setTicker(ticker);
        stockSymbol.setName(name);
        stockSymbol.setMarket(MarketType.KRX);
        stockSymbol.setAssetType(AssetType.STOCK);
        stockSymbol.setActive(true);
        stockSymbol.setSectorCode(sectorCode);
        stockSymbol.setSectorName(sectorName);
        stockSymbol.setCurrencyCode("KRW");
        stockSymbol.setPrimaryExchangeCode("XKRX");
        stockSymbol.setSourceProvider(StockDataProvider.KIS);
        stockSymbol.setSourceIdentifier(ticker);
        stockSymbol.setLastMasterSyncedAt(LocalDateTime.now());
        return stockSymbol;
    }
}
