package com.folo.stock;

import com.folo.common.enums.AssetType;
import com.folo.common.enums.MarketType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@TestPropertySource(properties = {
        "integration.opendart.enabled=true",
        "integration.opendart.api-key=test-api-key",
        "integration.opendart.base-url=https://opendart.fss.or.kr/api"
})
class StockIssuerProfileSyncServiceTest {

    @Autowired
    private StockIssuerProfileSyncService stockIssuerProfileSyncService;

    @Autowired
    private StockSymbolRepository stockSymbolRepository;

    @Autowired
    private StockIssuerProfileRepository stockIssuerProfileRepository;

    @Autowired
    private StockSymbolSyncRunRepository stockSymbolSyncRunRepository;

    @MockBean
    private OpendartClient opendartClient;

    @Test
    void syncSymbolsUpsertsIssuerProfilesAndRecordsSyncRun() {
        StockSymbol stockSymbol = new StockSymbol();
        stockSymbol.setMarket(MarketType.KRX);
        stockSymbol.setTicker("123456");
        stockSymbol.setName("테스트전자");
        stockSymbol.setAssetType(AssetType.STOCK);
        stockSymbol.setActive(true);
        stockSymbol.setPrimaryExchangeCode("XKRX");
        stockSymbol.setCurrencyCode("KRW");
        stockSymbol.setSourceProvider(StockDataProvider.KIS);
        stockSymbol.setSourceIdentifier("123456");
        stockSymbol.setLastMasterSyncedAt(LocalDateTime.now());
        stockSymbol = stockSymbolRepository.save(stockSymbol);

        given(opendartClient.isConfigured()).willReturn(true);
        given(opendartClient.fetchCorpCodes()).willReturn(Map.of(
                "123456",
                new OpendartCorpCodeRecord(
                        "00999999",
                        "테스트전자",
                        "123456",
                        LocalDate.of(2026, 3, 20),
                        "opendart:v1/corpCode"
                )
        ));
        given(opendartClient.fetchCompany("00999999")).willReturn(new OpendartCompanyRecord(
                "00999999",
                "테스트전자",
                "123456",
                "Y",
                "https://example.com",
                "https://ir.example.com",
                "264",
                "opendart:v1/company"
        ));

        stockIssuerProfileSyncService.syncSymbols(List.of(stockSymbol.getId()));

        StockIssuerProfile profile = stockIssuerProfileRepository.findByStockSymbolIdAndProvider(
                stockSymbol.getId(),
                StockDataProvider.OPENDART
        ).orElseThrow();

        assertThat(profile.getCorpCode()).isEqualTo("00999999");
        assertThat(profile.getCorpName()).isEqualTo("테스트전자");
        assertThat(profile.getStockCode()).isEqualTo("123456");
        assertThat(profile.getHmUrl()).isEqualTo("https://example.com");
        assertThat(profile.getIndutyCode()).isEqualTo("264");
        assertThat(stockSymbolSyncRunRepository.findAll()).hasSize(1);
        assertThat(stockSymbolSyncRunRepository.findAll().get(0).getSyncScope())
                .isEqualTo(StockSymbolSyncScope.ISSUER_PROFILE);
        assertThat(stockSymbolSyncRunRepository.findAll().get(0).getStatus())
                .isEqualTo(StockSymbolSyncStatus.COMPLETED);
    }
}
