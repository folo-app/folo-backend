package com.folo.stock;

import com.folo.common.enums.AssetType;
import com.folo.common.enums.MarketType;
import com.folo.config.MarketDataSyncProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class KisDomesticMasterMetadataEnrichmentProviderTest {

    @TempDir
    Path tempDir;

    @Test
    void fetchMetadataReadsSectorAndIndustryFromNormalizedMasterCsv() throws IOException {
        Path csv = tempDir.resolve("kis-domestic-master.csv");
        Files.writeString(csv, """
                ticker,name,assetType,primaryExchangeCode,currencyCode,sourceIdentifier,active,sectorName,industryName,sourcePayloadVersion
                005930,삼성전자,STOCK,XKRX,KRW,005930,true,Technology,Semiconductors,kis:domestic-master:v2
                """);

        KisDomesticMasterMetadataEnrichmentProvider provider = new KisDomesticMasterMetadataEnrichmentProvider(properties(csv));
        StockMetadataEnrichmentRecord record = provider.fetchMetadata(stockSymbol("005930"));

        assertThat(provider.isConfigured()).isTrue();
        assertThat(provider.supports(MarketType.KRX)).isTrue();
        assertThat(record.classificationScheme()).isEqualTo(StockClassificationScheme.KIS_MASTER);
        assertThat(record.sectorNameRaw()).isEqualTo("Technology");
        assertThat(record.industryNameRaw()).isEqualTo("Semiconductors");
        assertThat(record.sourcePayloadVersion()).isEqualTo("kis:domestic-master:v2");
        assertThat(record.representativeSectorName()).isEqualTo("Technology");
    }

    @Test
    void fetchMetadataFallsBackSafelyWhenLegacyMasterCsvHasNoEnrichmentColumns() throws IOException {
        Path csv = tempDir.resolve("kis-domestic-master-legacy.csv");
        Files.writeString(csv, """
                ticker,name,assetType,primaryExchangeCode,currencyCode,sourceIdentifier,active
                005930,삼성전자,STOCK,XKRX,KRW,005930,true
                """);

        KisDomesticMasterMetadataEnrichmentProvider provider = new KisDomesticMasterMetadataEnrichmentProvider(properties(csv));
        StockMetadataEnrichmentRecord record = provider.fetchMetadata(stockSymbol("005930"));

        assertThat(record.classificationScheme()).isEqualTo(StockClassificationScheme.KIS_MASTER);
        assertThat(record.sectorNameRaw()).isNull();
        assertThat(record.industryNameRaw()).isNull();
        assertThat(record.sourcePayloadVersion()).isEqualTo("kis:domestic-master:v2");
    }

    private MarketDataSyncProperties properties(Path domesticMasterFile) {
        return new MarketDataSyncProperties(
                false,
                false,
                "0 0 4 * * *",
                "0 30 4 * * *",
                "0 0 5 * * *",
                "Asia/Seoul",
                500,
                new MarketDataSyncProperties.TwelveData(false, null, null),
                new MarketDataSyncProperties.Polygon(false, false, false, null, null),
                new MarketDataSyncProperties.Kis(
                        true,
                        false,
                        "https://openapi.koreainvestment.com:9443",
                        "app-key",
                        "app-secret",
                        domesticMasterFile.toString(),
                        null
                )
        );
    }

    private StockSymbol stockSymbol(String ticker) {
        StockSymbol stockSymbol = new StockSymbol();
        stockSymbol.setId(1L);
        stockSymbol.setTicker(ticker);
        stockSymbol.setName("Sample " + ticker);
        stockSymbol.setMarket(MarketType.KRX);
        stockSymbol.setAssetType(AssetType.STOCK);
        stockSymbol.setActive(true);
        stockSymbol.setCurrencyCode("KRW");
        stockSymbol.setPrimaryExchangeCode("XKRX");
        stockSymbol.setSourceProvider(StockDataProvider.KIS);
        stockSymbol.setSourceIdentifier(ticker);
        stockSymbol.setLastMasterSyncedAt(LocalDateTime.now());
        return stockSymbol;
    }
}
