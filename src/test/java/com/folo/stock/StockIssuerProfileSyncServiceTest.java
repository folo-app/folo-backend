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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

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

    @Test
    void syncMissingActiveSymbolsSkipsPreferredSharesAndAlreadySyncedSymbols() {
        StockSymbol commonStock = saveStock("123456", "테스트전자");
        StockSymbol preferredStock = saveStock("123457", "테스트전자우");
        StockSymbol alreadySyncedStock = saveStock("654321", "기존상장사");

        StockIssuerProfile existingProfile = new StockIssuerProfile();
        existingProfile.setStockSymbol(alreadySyncedStock);
        existingProfile.setProvider(StockDataProvider.OPENDART);
        existingProfile.setCorpCode("00111111");
        existingProfile.setStockCode("654321");
        existingProfile.setCorpName("기존상장사");
        existingProfile.setLastSyncedAt(LocalDateTime.now());
        stockIssuerProfileRepository.save(existingProfile);

        given(opendartClient.isConfigured()).willReturn(true);
        given(opendartClient.fetchCorpCodes()).willReturn(Map.of(
                "123456",
                new OpendartCorpCodeRecord(
                        "00999999",
                        "테스트전자",
                        "123456",
                        LocalDate.of(2026, 3, 20),
                        "opendart:v1/corpCode"
                ),
                "123457",
                new OpendartCorpCodeRecord(
                        "00888888",
                        "테스트전자우",
                        "123457",
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

        stockIssuerProfileSyncService.syncMissingActiveSymbols();

        assertThat(stockIssuerProfileRepository.findByStockSymbolIdAndProvider(commonStock.getId(), StockDataProvider.OPENDART))
                .isPresent();
        assertThat(stockIssuerProfileRepository.findByStockSymbolIdAndProvider(preferredStock.getId(), StockDataProvider.OPENDART))
                .isEmpty();
        verify(opendartClient, never()).fetchCompany("00888888");
    }

    @Test
    void syncAllActiveSymbolsBackfillsAllNonPreferredActiveCommonStocks() {
        StockSymbol first = saveStock("123456", "테스트전자");
        StockSymbol second = saveStock("654321", "테스트제약");

        given(opendartClient.isConfigured()).willReturn(true);
        given(opendartClient.fetchCorpCodes()).willReturn(Map.of(
                "123456",
                new OpendartCorpCodeRecord("00999999", "테스트전자", "123456", LocalDate.of(2026, 3, 20), "opendart:v1/corpCode"),
                "654321",
                new OpendartCorpCodeRecord("00888888", "테스트제약", "654321", LocalDate.of(2026, 3, 20), "opendart:v1/corpCode")
        ));
        given(opendartClient.fetchCompany("00999999")).willReturn(new OpendartCompanyRecord(
                "00999999", "테스트전자", "123456", "Y", null, null, "264", "opendart:v1/company"
        ));
        given(opendartClient.fetchCompany("00888888")).willReturn(new OpendartCompanyRecord(
                "00888888", "테스트제약", "654321", "Y", null, null, "212", "opendart:v1/company"
        ));

        stockIssuerProfileSyncService.syncAllActiveSymbols();

        assertThat(stockIssuerProfileRepository.findByStockSymbolIdAndProvider(first.getId(), StockDataProvider.OPENDART)).isPresent();
        assertThat(stockIssuerProfileRepository.findByStockSymbolIdAndProvider(second.getId(), StockDataProvider.OPENDART)).isPresent();
        assertThat(stockSymbolSyncRunRepository.findAll()).hasSize(1);
    }

    private StockSymbol saveStock(String ticker, String name) {
        StockSymbol stockSymbol = new StockSymbol();
        stockSymbol.setMarket(MarketType.KRX);
        stockSymbol.setTicker(ticker);
        stockSymbol.setName(name);
        stockSymbol.setAssetType(AssetType.STOCK);
        stockSymbol.setActive(true);
        stockSymbol.setPrimaryExchangeCode("XKRX");
        stockSymbol.setCurrencyCode("KRW");
        stockSymbol.setSourceProvider(StockDataProvider.KIS);
        stockSymbol.setSourceIdentifier(ticker);
        stockSymbol.setLastMasterSyncedAt(LocalDateTime.now());
        return stockSymbolRepository.save(stockSymbol);
    }
}
