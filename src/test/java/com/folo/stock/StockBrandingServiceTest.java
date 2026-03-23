package com.folo.stock;

import com.folo.common.enums.AssetType;
import com.folo.common.enums.MarketType;
import com.folo.config.MarketDataSyncProperties;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

class StockBrandingServiceTest {

    @Test
    void getPublicLogoUrlReturnsNullForKrxStocks() {
        StockBrandingService service = new StockBrandingService(
                RestClient.builder(),
                properties(false, false)
        );

        StockSymbol stockSymbol = stock(11L, "005930", "삼성전자", MarketType.KRX);

        assertThat(service.getPublicLogoUrl(stockSymbol)).isNull();
    }

    @Test
    void getPublicLogoUrlReturnsDynamicLogoEndpointForUsStocks() {
        StockBrandingService service = new StockBrandingService(
                RestClient.builder(),
                properties(true, false)
        );

        StockSymbol stockSymbol = stock(12L, "AAPL", "Apple Inc.", MarketType.NASDAQ);
        stockSymbol.setPrimaryExchangeCode("NAS");

        assertThat(service.getPublicLogoUrl(stockSymbol))
                .isEqualTo("/stocks/AAPL/logo?market=NASDAQ&micCode=XNAS");
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
