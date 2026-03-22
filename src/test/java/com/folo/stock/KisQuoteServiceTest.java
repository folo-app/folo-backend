package com.folo.stock;

import com.folo.common.enums.AssetType;
import com.folo.common.enums.MarketType;
import com.folo.config.MarketDataSyncProperties;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KisQuoteServiceTest {

    @Test
    void fetchQuotesFallsBackToEmptyWhenAccessTokenFails() {
        KisAccessTokenService accessTokenService = mock(KisAccessTokenService.class);
        when(accessTokenService.isConfigured()).thenReturn(true);
        when(accessTokenService.getAccessToken()).thenThrow(new IllegalStateException("token unavailable"));

        KisQuoteService service = new KisQuoteService(
                RestClient.builder(),
                properties(),
                accessTokenService
        );

        StockSymbol stock = new StockSymbol();
        stock.setId(1L);
        stock.setMarket(MarketType.NASDAQ);
        stock.setTicker("MSFT");
        stock.setName("Microsoft Corp");
        stock.setAssetType(AssetType.STOCK);
        stock.setActive(true);

        Map<Long, ResolvedStockQuote> result = service.fetchQuotes(List.of(stock));

        assertEquals(Map.of(), result);
    }

    private MarketDataSyncProperties properties() {
        return new MarketDataSyncProperties(
                true,
                false,
                "0 0 4 * * *",
                "0 30 4 * * *",
                "0 0 5 * * *",
                "Asia/Seoul",
                500,
                new MarketDataSyncProperties.TwelveData(false, "", "https://api.twelvedata.com"),
                new MarketDataSyncProperties.Polygon(false, false, false, "", "https://api.polygon.io"),
                new MarketDataSyncProperties.Kis(true, false, "https://openapi.koreainvestment.com:9443", "app", "secret", "", "")
        );
    }
}
