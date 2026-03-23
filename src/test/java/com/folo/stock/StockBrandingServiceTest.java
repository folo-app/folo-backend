package com.folo.stock;

import com.folo.common.enums.AssetType;
import com.folo.common.enums.MarketType;
import com.folo.config.MarketDataSyncProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockBrandingServiceTest {

    @Mock
    private StockBrandAssetRepository stockBrandAssetRepository;

    @Mock
    private StockSymbolRepository stockSymbolRepository;

    @Test
    void getPublicLogoUrlReturnsStoredKrxLogoWithoutFrontendContractChange() {
        StockBrandAsset asset = new StockBrandAsset();
        asset.setPublicUrl("/uploads/stock-logos/krx/005930-logo.png");

        when(stockBrandAssetRepository.findByStockSymbolId(11L)).thenReturn(Optional.of(asset));

        StockBrandingService service = new StockBrandingService(
                RestClient.builder(),
                properties(),
                stockBrandAssetRepository,
                stockSymbolRepository,
                new KrxBrandFamilyResolver()
        );

        StockSymbol stockSymbol = new StockSymbol();
        stockSymbol.setId(11L);
        stockSymbol.setTicker("005930");
        stockSymbol.setName("삼성전자");
        stockSymbol.setMarket(MarketType.KRX);
        stockSymbol.setAssetType(AssetType.STOCK);
        stockSymbol.setActive(true);

        assertThat(service.getPublicLogoUrl(stockSymbol))
                .isEqualTo("/uploads/stock-logos/krx/005930-logo.png");
    }

    @Test
    void getPublicLogoUrlReturnsCommonStockLogoForPreferredShare() {
        StockBrandAsset commonAsset = new StockBrandAsset();
        commonAsset.setPublicUrl("/uploads/stock-logos/krx/000100-common.png");

        StockSymbol commonStock = krxStock(100L, "000100", "유한양행");

        when(stockSymbolRepository.findByMarketAndName(MarketType.KRX, "유한양행"))
                .thenReturn(Optional.of(commonStock));
        when(stockBrandAssetRepository.findByStockSymbolId(100L))
                .thenReturn(Optional.of(commonAsset));

        StockBrandingService service = new StockBrandingService(
                RestClient.builder(),
                properties(),
                stockBrandAssetRepository,
                stockSymbolRepository,
                new KrxBrandFamilyResolver()
        );

        StockSymbol preferredStock = krxStock(101L, "000105", "유한양행우");

        assertThat(service.getPublicLogoUrl(preferredStock))
                .isEqualTo("/uploads/stock-logos/krx/000100-common.png");
    }

    @Test
    void getPublicLogoUrlReturnsFamilyRepresentativeLogoForAffiliate() {
        StockBrandAsset representativeAsset = new StockBrandAsset();
        representativeAsset.setStockSymbol(krxStock(3550L, "003550", "LG"));
        representativeAsset.setPublicUrl("/uploads/stock-logos/krx/lg-family.png");

        StockBrandAsset affiliateAsset = new StockBrandAsset();
        affiliateAsset.setStockSymbol(krxStock(66570L, "066570", "LG전자"));
        affiliateAsset.setPublicUrl("/uploads/stock-logos/krx/lg-electronics.png");

        when(stockSymbolRepository.findByMarketAndTickerInAndActiveTrue(MarketType.KRX, List.of("003550")))
                .thenReturn(List.of(krxStock(3550L, "003550", "LG")));
        when(stockBrandAssetRepository.findAllByStockSymbolIdIn(anyList()))
                .thenReturn(List.of(representativeAsset, affiliateAsset));

        StockBrandingService service = new StockBrandingService(
                RestClient.builder(),
                properties(),
                stockBrandAssetRepository,
                stockSymbolRepository,
                new KrxBrandFamilyResolver()
        );

        StockSymbol affiliate = krxStock(66570L, "066570", "LG전자");

        assertThat(service.getPublicLogoUrl(affiliate))
                .isEqualTo("/uploads/stock-logos/krx/lg-family.png");
    }

    @Test
    void getPublicLogoUrlDoesNotReuseAffiliateSpecificFamilyLogoWhenRepresentativeIsMissing() {
        when(stockSymbolRepository.findByMarketAndTickerInAndActiveTrue(MarketType.KRX, List.of("005930", "028260")))
                .thenReturn(List.of(
                        krxStock(5930L, "005930", "삼성전자"),
                        krxStock(28260L, "028260", "삼성물산")
                ));
        when(stockBrandAssetRepository.findAllByStockSymbolIdIn(anyList()))
                .thenReturn(List.of());

        StockBrandingService service = new StockBrandingService(
                RestClient.builder(),
                properties(),
                stockBrandAssetRepository,
                stockSymbolRepository,
                new KrxBrandFamilyResolver()
        );

        StockSymbol samsungElectronics = krxStock(5930L, "005930", "삼성전자");

        assertThat(service.getPublicLogoUrl(samsungElectronics))
                .isNull();
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
                new MarketDataSyncProperties.Kis(false, false, null, null, null, null, null)
        );
    }

    private StockSymbol krxStock(Long id, String ticker, String name) {
        StockSymbol stockSymbol = new StockSymbol();
        stockSymbol.setId(id);
        stockSymbol.setTicker(ticker);
        stockSymbol.setName(name);
        stockSymbol.setMarket(MarketType.KRX);
        stockSymbol.setAssetType(AssetType.STOCK);
        stockSymbol.setActive(true);
        return stockSymbol;
    }
}
