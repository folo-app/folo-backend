package com.folo.stock;

import com.folo.common.enums.AssetType;
import com.folo.common.enums.MarketType;
import com.folo.config.MarketDataSyncProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class KisDomesticDividendSyncProviderTest {

    @Test
    void fetchEventsMapsKisDividendScheduleResponse() {
        RestClient.Builder tokenBuilder = RestClient.builder();
        MockRestServiceServer tokenServer = MockRestServiceServer.bindTo(tokenBuilder).build();
        tokenServer.expect(request -> {
                    assertThat(request.getURI().getPath()).isEqualTo("/oauth2/tokenP");
                })
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                          "access_token": "kis-access-token",
                          "expires_in": "3600"
                        }
                        """, MediaType.APPLICATION_JSON));

        RestClient.Builder apiBuilder = RestClient.builder();
        MockRestServiceServer apiServer = MockRestServiceServer.bindTo(apiBuilder).build();
        apiServer.expect(request -> {
                    assertThat(request.getURI().getPath()).isEqualTo("/uapi/domestic-stock/v1/ksdinfo/dividend");
                    assertThat(request.getURI().getQuery()).contains("GB1=0");
                    assertThat(request.getURI().getQuery()).contains("SHT_CD=005930");
                    assertThat(request.getURI().getQuery()).contains("F_DT=20240101");
                    assertThat(request.getURI().getQuery()).contains("HIGH_GB=0");
                    HttpHeaders headers = request.getHeaders();
                    assertThat(headers.getFirst("authorization")).isEqualTo("Bearer kis-access-token");
                    assertThat(headers.getFirst("appkey")).isEqualTo("app-key");
                    assertThat(headers.getFirst("appsecret")).isEqualTo("app-secret");
                    assertThat(headers.getFirst("tr_id")).isEqualTo("HHKDB669102C0");
                })
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "output1": [
                            {
                              "sht_cd": "005930",
                              "record_date": "20240331",
                              "divi_pay_dt": "20240419",
                              "divi_kind": "현금배당",
                              "per_sto_divi_amt": "361",
                              "divi_rate": "2.1",
                              "stk_divi_rate": "0",
                              "high_divi_gb": "0"
                            },
                            {
                              "sht_cd": "005930",
                              "record_date": "20230331",
                              "divi_pay_dt": "20230419",
                              "divi_kind": "주식배당",
                              "per_sto_divi_amt": "0",
                              "stk_divi_rate": "0.03",
                              "high_divi_gb": "0"
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        MarketDataSyncProperties properties = properties();
        KisAccessTokenService accessTokenService = new KisAccessTokenService(tokenBuilder, properties);
        KisDomesticDividendSyncProvider provider = new KisDomesticDividendSyncProvider(apiBuilder, properties, accessTokenService);

        List<DividendEventRecord> events = provider.fetchEvents(stockSymbol(), LocalDate.of(2024, 1, 1));

        assertThat(provider.isConfigured()).isTrue();
        assertThat(provider.supports(MarketType.KRX)).isTrue();
        assertThat(events).hasSize(1);
        assertThat(events.get(0).eventType()).isEqualTo(DividendEventType.CASH);
        assertThat(events.get(0).recordDate()).isEqualTo(LocalDate.of(2024, 3, 31));
        assertThat(events.get(0).payDate()).isEqualTo(LocalDate.of(2024, 4, 19));
        assertThat(events.get(0).cashAmount()).isEqualByComparingTo("361");
        assertThat(events.get(0).currencyCode()).isEqualTo("KRW");

        tokenServer.verify();
        apiServer.verify();
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
                new MarketDataSyncProperties.Polygon(false, false, false, null, null),
                new MarketDataSyncProperties.Kis(
                        true,
                        true,
                        "https://openapi.koreainvestment.com:9443",
                        "app-key",
                        "app-secret",
                        null,
                        null
                )
        );
    }

    private StockSymbol stockSymbol() {
        StockSymbol stockSymbol = new StockSymbol();
        stockSymbol.setId(1L);
        stockSymbol.setTicker("005930");
        stockSymbol.setName("삼성전자");
        stockSymbol.setMarket(MarketType.KRX);
        stockSymbol.setAssetType(AssetType.STOCK);
        stockSymbol.setActive(true);
        stockSymbol.setCurrencyCode("KRW");
        stockSymbol.setPrimaryExchangeCode("XKRX");
        stockSymbol.setSourceProvider(StockDataProvider.KIS);
        stockSymbol.setSourceIdentifier("005930");
        stockSymbol.setLastMasterSyncedAt(LocalDateTime.now());
        return stockSymbol;
    }
}
