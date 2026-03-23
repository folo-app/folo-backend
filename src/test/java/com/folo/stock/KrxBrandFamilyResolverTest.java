package com.folo.stock;

import com.folo.common.enums.AssetType;
import com.folo.common.enums.MarketType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KrxBrandFamilyResolverTest {

    private final KrxBrandFamilyResolver resolver = new KrxBrandFamilyResolver();

    @Test
    void resolveMatchesConfiguredKrxFamilies() {
        assertThat(resolver.resolve(krxStock("066570", "LG전자")).key()).isEqualTo("LG");
        assertThat(resolver.resolve(krxStock("000660", "SK하이닉스")).key()).isEqualTo("SK");
        assertThat(resolver.resolve(krxStock("000990", "DB하이텍")).key()).isEqualTo("DB");
        assertThat(resolver.resolve(krxStock("005490", "POSCO홀딩스")).key()).isEqualTo("POSCO");
        assertThat(resolver.resolve(krxStock("298020", "효성티앤씨")).key()).isEqualTo("HYOSUNG");
        assertThat(resolver.resolve(krxStock("086520", "에코프로")).key()).isEqualTo("ECOPRO");
        assertThat(resolver.resolve(krxStock("267250", "HD현대일렉트릭")).key()).isEqualTo("HD_HYUNDAI");
        assertThat(resolver.resolve(krxStock("035720", "카카오페이")).key()).isEqualTo("KAKAO");
        assertThat(resolver.resolve(krxStock("068270", "셀트리온제약")).key()).isEqualTo("CELLTRION");
        assertThat(resolver.resolve(krxStock("028300", "HLB제약")).key()).isEqualTo("HLB");
        assertThat(resolver.resolve(krxStock("060980", "HL만도")).key()).isEqualTo("HL");
        assertThat(resolver.resolve(krxStock("005380", "현대차우")).key()).isEqualTo("HYUNDAI_MOTOR");
        assertThat(resolver.resolve(krxStock("069960", "현대백화점")).key()).isEqualTo("HYUNDAI_DEPARTMENT");
        assertThat(resolver.resolve(krxStock("001450", "현대해상")).key()).isEqualTo("HYUNDAI_MARINE_FIRE");
    }

    @Test
    void resolveCommonStockNameStripsPreferredShareSuffix() {
        assertThat(resolver.resolveCommonStockName("CJ4우(전환)")).isEqualTo("CJ");
        assertThat(resolver.resolveCommonStockName("한화3우B")).isEqualTo("한화");
        assertThat(resolver.resolveCommonStockName("유한양행우")).isEqualTo("유한양행");
    }

    private StockSymbol krxStock(String ticker, String name) {
        StockSymbol stockSymbol = new StockSymbol();
        stockSymbol.setTicker(ticker);
        stockSymbol.setName(name);
        stockSymbol.setMarket(MarketType.KRX);
        stockSymbol.setAssetType(AssetType.STOCK);
        stockSymbol.setActive(true);
        return stockSymbol;
    }
}
