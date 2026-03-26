package com.folo.stock;

import com.folo.common.enums.AssetType;
import com.folo.common.enums.MarketType;
import com.folo.config.FileStorageProperties;
import com.folo.config.MarketDataSyncProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StockBrandingServiceTest {

    @Test
    void getPublicLogoUrlReturnsNullForKrxStocks() {
        StockBrandingService service = new StockBrandingService(
                RestClient.builder(),
                properties(false, false),
                storageProperties(null),
                stockSymbolRepository()
        );

        StockSymbol stockSymbol = stock(11L, "005930", "삼성전자", MarketType.KRX);

        assertThat(service.getPublicLogoUrl(stockSymbol)).isNull();
    }

    @Test
    void getPublicLogoUrlReturnsDynamicLogoEndpointForUsStocks() {
        StockBrandingService service = new StockBrandingService(
                RestClient.builder(),
                properties(true, false),
                storageProperties(null),
                stockSymbolRepository()
        );

        StockSymbol stockSymbol = stock(12L, "AAPL", "Apple Inc.", MarketType.NASDAQ);
        stockSymbol.setPrimaryExchangeCode("NAS");

        assertThat(service.getPublicLogoUrl(stockSymbol))
                .isEqualTo("/stocks/AAPL/logo?market=NASDAQ&micCode=XNAS");
    }

    @Test
    void getPublicLogoUrlReturnsEndpointForKrxStocksWhenLocalLogoExists(@TempDir Path tempDir) throws IOException {
        Files.createDirectories(tempDir.resolve("KOSPI-logo"));
        Files.write(tempDir.resolve("KOSPI-logo/005930.png"), new byte[]{1, 2, 3});

        StockBrandingService service = new StockBrandingService(
                RestClient.builder(),
                properties(false, false),
                storageProperties(tempDir.toString()),
                stockSymbolRepository()
        );

        StockSymbol stockSymbol = stock(11L, "005930", "삼성전자", MarketType.KRX);

        assertThat(service.getPublicLogoUrl(stockSymbol))
                .isEqualTo("/stocks/005930/logo?market=KRX");
    }

    @Test
    void fetchLogoReturnsLocalKrxLogoPayload(@TempDir Path tempDir) throws IOException {
        Files.createDirectories(tempDir.resolve("KOSDAQ-logo"));
        byte[] bytes = new byte[]{4, 5, 6};
        Files.write(tempDir.resolve("KOSDAQ-logo/066980.png"), bytes);

        StockSymbolRepository stockSymbolRepository = stockSymbolRepository(
                stock(31L, "066980", "브레인즈컴퍼니", MarketType.KRX)
        );
        StockBrandingService service = new StockBrandingService(
                RestClient.builder(),
                properties(false, false),
                storageProperties(tempDir.toString()),
                stockSymbolRepository
        );

        StockBrandingService.LogoPayload payload = service.fetchLogo("066980", MarketType.KRX);

        assertThat(payload.bytes()).isEqualTo(bytes);
        assertThat(payload.contentType()).isEqualTo("image/png");
    }

    @Test
    void getPublicLogoUrlReturnsEndpointForKrxPreferredTickerWhenBaseLogoExists(@TempDir Path tempDir) throws IOException {
        Files.createDirectories(tempDir.resolve("KOSPI-logo"));
        Files.write(tempDir.resolve("KOSPI-logo/000880.png"), new byte[]{7, 8, 9});

        StockBrandingService service = new StockBrandingService(
                RestClient.builder(),
                properties(false, false),
                storageProperties(tempDir.toString()),
                stockSymbolRepository()
        );

        StockSymbol stockSymbol = stock(21L, "00088K", "한화3우B", MarketType.KRX);

        assertThat(service.getPublicLogoUrl(stockSymbol))
                .isEqualTo("/stocks/00088K/logo?market=KRX");
    }

    @Test
    void fetchLogoFallsBackToBaseTickerForKrxPreferredTicker(@TempDir Path tempDir) throws IOException {
        Files.createDirectories(tempDir.resolve("KOSPI-logo"));
        byte[] bytes = new byte[]{10, 11, 12};
        Files.write(tempDir.resolve("KOSPI-logo/000880.png"), bytes);

        StockSymbolRepository stockSymbolRepository = stockSymbolRepository(
                stock(21L, "00088K", "한화3우B", MarketType.KRX)
        );
        StockBrandingService service = new StockBrandingService(
                RestClient.builder(),
                properties(false, false),
                storageProperties(tempDir.toString()),
                stockSymbolRepository
        );

        StockBrandingService.LogoPayload payload = service.fetchLogo("00088K", MarketType.KRX);

        assertThat(payload.bytes()).isEqualTo(bytes);
        assertThat(payload.contentType()).isEqualTo("image/png");
    }

    @Test
    void getPublicLogoUrlReturnsEndpointForNumericKrxPreferredTickerWhenBaseLogoExists(@TempDir Path tempDir) throws IOException {
        Files.createDirectories(tempDir.resolve("KOSPI-logo"));
        Files.write(tempDir.resolve("KOSPI-logo/009830.png"), new byte[]{13, 14, 15});

        StockBrandingService service = new StockBrandingService(
                RestClient.builder(),
                properties(false, false),
                storageProperties(tempDir.toString()),
                stockSymbolRepository()
        );

        StockSymbol stockSymbol = stock(41L, "009835", "한화솔루션우", MarketType.KRX);

        assertThat(service.getPublicLogoUrl(stockSymbol))
                .isEqualTo("/stocks/009835/logo?market=KRX");
    }

    @Test
    void fetchLogoFallsBackToBaseTickerForNumericKrxPreferredTicker(@TempDir Path tempDir) throws IOException {
        Files.createDirectories(tempDir.resolve("KOSPI-logo"));
        byte[] bytes = new byte[]{16, 17, 18};
        Files.write(tempDir.resolve("KOSPI-logo/003530.png"), bytes);

        StockSymbol preferredSymbol = stock(42L, "003535", "한화투자증권우", MarketType.KRX);
        StockBrandingService service = new StockBrandingService(
                RestClient.builder(),
                properties(false, false),
                storageProperties(tempDir.toString()),
                stockSymbolRepository(preferredSymbol)
        );

        StockBrandingService.LogoPayload payload = service.fetchLogo("003535", MarketType.KRX);

        assertThat(payload.bytes()).isEqualTo(bytes);
        assertThat(payload.contentType()).isEqualTo("image/png");
    }

    private MarketDataSyncProperties properties(boolean twelveDataEnabled, boolean polygonEnabled) {
        return new MarketDataSyncProperties(
                false,
                false,
                "0 0 4 * * *",
                "0 30 4 * * *",
                "0 0 5 * * *",
                "Asia/Seoul",
                500,
                new MarketDataSyncProperties.TwelveData(
                        twelveDataEnabled,
                        "test-key",
                        "https://api.twelvedata.com"
                ),
                new MarketDataSyncProperties.Polygon(
                        false,
                        polygonEnabled,
                        polygonEnabled,
                        "test-polygon-key",
                        "https://api.polygon.io"
                ),
                new MarketDataSyncProperties.Kis(false, false, null, null, null, null, null)
        );
    }

    private FileStorageProperties storageProperties(String stockLogoRootDir) {
        return new FileStorageProperties("/tmp/folo-uploads", stockLogoRootDir);
    }

    private StockSymbolRepository stockSymbolRepository(StockSymbol... symbols) {
        StockSymbolRepository repository = mock(StockSymbolRepository.class);
        when(repository.findByMarketAndTicker(eq(MarketType.KRX), anyString())).thenReturn(Optional.empty());
        for (StockSymbol symbol : symbols) {
            when(repository.findByMarketAndTicker(symbol.getMarket(), symbol.getTicker()))
                    .thenReturn(Optional.of(symbol));
        }
        return repository;
    }

    private StockSymbol stock(Long id, String ticker, String name, MarketType market) {
        StockSymbol stockSymbol = new StockSymbol();
        stockSymbol.setId(id);
        stockSymbol.setTicker(ticker);
        stockSymbol.setName(name);
        stockSymbol.setMarket(market);
        stockSymbol.setAssetType(AssetType.STOCK);
        stockSymbol.setActive(true);
        return stockSymbol;
    }
}
