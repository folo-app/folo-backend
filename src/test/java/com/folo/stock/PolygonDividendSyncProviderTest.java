package com.folo.stock;

import com.folo.common.enums.AssetType;
import com.folo.common.enums.MarketType;
import com.folo.config.MarketDataSyncProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class PolygonDividendSyncProviderTest {

    @Test
    void fetchEventsMapsPolygonDividendResults() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(request -> {
                    assertThat(request.getURI().getPath()).isEqualTo("/v3/reference/dividends");
                    assertThat(request.getURI().getQuery()).contains("ticker=AAPL");
                    assertThat(request.getURI().getQuery()).contains("apiKey=test-api-key");
                })
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "results": [
                            {
                              "id": "evt-1",
                              "cash_amount": 0.25,
                              "currency": "usd",
                              "declaration_date": "2026-01-01",
                              "ex_dividend_date": "2026-02-10",
                              "frequency": 4,
                              "pay_date": "2026-02-14",
                              "record_date": "2026-02-12",
                              "dividend_type": "CD"
                            },
                            {
                              "id": "evt-2",
                              "cash_amount": 1.5,
                              "currency": "usd",
                              "declaration_date": "2025-11-01",
                              "ex_dividend_date": "2025-12-10",
                              "frequency": 0,
                              "pay_date": "2025-12-14",
                              "record_date": "2025-12-12",
                              "dividend_type": "SC"
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        PolygonDividendSyncProvider provider = new PolygonDividendSyncProvider(builder, properties());
        List<DividendEventRecord> events = provider.fetchEvents(stockSymbol(), LocalDate.of(2025, 1, 1));

        assertThat(events).hasSize(2);
        assertThat(events.get(0).eventType()).isEqualTo(DividendEventType.CASH);
        assertThat(events.get(0).cashAmount()).isEqualByComparingTo("0.25");
        assertThat(events.get(0).payDate()).isEqualTo(LocalDate.of(2026, 2, 14));
        assertThat(events.get(0).currencyCode()).isEqualTo("USD");
        assertThat(events.get(0).frequencyRaw()).isEqualTo("4");
        assertThat(events.get(1).eventType()).isEqualTo(DividendEventType.SPECIAL_CASH);
        assertThat(events.get(1).cashAmount()).isEqualByComparingTo("1.5");

        server.verify();
    }

    private MarketDataSyncProperties properties() {
        return new MarketDataSyncProperties(
                false,
                false,
                "0 0 4 * * *",
                "0 30 4 * * *",
                "0 0 5 * * *",
                "Asia/Seoul",
                500,
                new MarketDataSyncProperties.TwelveData(false, null, null),
                new MarketDataSyncProperties.Polygon(false, true, false, "test-api-key", "https://api.polygon.io"),
                new MarketDataSyncProperties.Kis(false, false, null, null, null, null, null)
        );
    }

    private StockSymbol stockSymbol() {
        StockSymbol stockSymbol = new StockSymbol();
        stockSymbol.setId(1L);
        stockSymbol.setTicker("AAPL");
        stockSymbol.setName("Apple Inc.");
        stockSymbol.setMarket(MarketType.NASDAQ);
        stockSymbol.setAssetType(AssetType.STOCK);
        stockSymbol.setActive(true);
        stockSymbol.setCurrencyCode("USD");
        stockSymbol.setPrimaryExchangeCode("XNAS");
        stockSymbol.setSourceProvider(StockDataProvider.POLYGON);
        stockSymbol.setSourceIdentifier("AAPL");
        stockSymbol.setLastMasterSyncedAt(LocalDateTime.now());
        return stockSymbol;
    }
}
