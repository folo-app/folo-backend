package com.folo.stock;

import com.folo.common.enums.AssetType;
import com.folo.common.enums.MarketType;
import com.folo.config.MarketDataSyncProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class PolygonTickerOverviewEnrichmentProviderTest {

    @Test
    void fetchMetadataMapsPolygonOverviewToSicSectorAndIndustry() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(request -> {
                    assertThat(request.getURI().getPath()).isEqualTo("/v3/reference/tickers/NVDA");
                    assertThat(request.getURI().getQuery()).contains("apiKey=test-api-key");
                })
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "results": {
                            "sic_code": "3674",
                            "sic_description": "SEMICONDUCTORS AND RELATED DEVICES"
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        PolygonTickerOverviewEnrichmentProvider provider = new PolygonTickerOverviewEnrichmentProvider(builder, properties());
        StockMetadataEnrichmentRecord record = provider.fetchMetadata(stockSymbol());

        assertThat(record.classificationScheme()).isEqualTo(StockClassificationScheme.SIC);
        assertThat(record.sectorNameRaw()).isEqualTo("Manufacturing");
        assertThat(record.industryNameRaw()).isEqualTo("SEMICONDUCTORS AND RELATED DEVICES");
        assertThat(record.representativeSectorName()).isEqualTo("Manufacturing");

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
                new MarketDataSyncProperties.Polygon(false, false, true, "test-api-key", "https://api.polygon.io"),
                new MarketDataSyncProperties.Kis(false, false, null, null, null, null, null)
        );
    }

    private StockSymbol stockSymbol() {
        StockSymbol stockSymbol = new StockSymbol();
        stockSymbol.setId(77L);
        stockSymbol.setTicker("NVDA");
        stockSymbol.setName("NVIDIA Corporation");
        stockSymbol.setMarket(MarketType.NASDAQ);
        stockSymbol.setAssetType(AssetType.STOCK);
        stockSymbol.setActive(true);
        stockSymbol.setCurrencyCode("USD");
        stockSymbol.setPrimaryExchangeCode("XNAS");
        stockSymbol.setSourceProvider(StockDataProvider.POLYGON);
        stockSymbol.setSourceIdentifier("NVDA");
        stockSymbol.setLastMasterSyncedAt(LocalDateTime.now());
        return stockSymbol;
    }
}
