package com.folo.stock;

import com.folo.common.enums.AssetType;
import com.folo.common.enums.MarketType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class StockIssuerProfileRepositoryTest {

    @Autowired
    private StockSymbolRepository stockSymbolRepository;

    @Autowired
    private StockIssuerProfileRepository stockIssuerProfileRepository;

    @Test
    void findsIssuerProfilesBySymbolAndCorpCode() {
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

        StockIssuerProfile profile = new StockIssuerProfile();
        profile.setStockSymbol(stockSymbol);
        profile.setProvider(StockDataProvider.OPENDART);
        profile.setCorpCode("00126380");
        profile.setCorpName("삼성전자");
        profile.setStockCode("005930");
        profile.setCorpCls("Y");
        profile.setHmUrl("https://www.samsung.com/sec/");
        profile.setIrUrl("https://www.samsung.com/global/ir/");
        profile.setIndutyCode("264");
        profile.setSourcePayloadVersion("opendart:v1/company");
        profile.setLastSyncedAt(LocalDateTime.now());
        stockIssuerProfileRepository.save(profile);

        assertThat(stockIssuerProfileRepository.findByStockSymbolIdAndProvider(
                stockSymbol.getId(),
                StockDataProvider.OPENDART
        )).isPresent();
        assertThat(stockIssuerProfileRepository.findByProviderAndCorpCode(
                StockDataProvider.OPENDART,
                "00126380"
        )).isPresent();
        assertThat(stockIssuerProfileRepository.findAllByStockSymbolIdIn(List.of(stockSymbol.getId())))
                .hasSize(1);
    }
}
