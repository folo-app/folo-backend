package com.folo.stock;

import com.folo.common.enums.AssetType;
import com.folo.common.enums.MarketType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KrxPreferredStockResolverTest {

    @Test
    void resolvesBaseCommonStockByTickerForNumericPreferredShare() {
        StockSymbolRepository repository = mock(StockSymbolRepository.class);
        StockSymbol common = stock(1L, "005930", "삼성전자");
        StockSymbol preferred = stock(2L, "005935", "삼성전자우");
        when(repository.findByMarketAndTicker(MarketType.KRX, "005930")).thenReturn(Optional.of(common));

        KrxPreferredStockResolver resolver = new KrxPreferredStockResolver(repository);

        assertThat(resolver.resolveBaseCommonStock(preferred)).isEqualTo(common);
    }

    @Test
    void resolvesBaseCommonStockByBaseNameForSuffixVariants() {
        StockSymbolRepository repository = mock(StockSymbolRepository.class);
        StockSymbol common = stock(1L, "001040", "CJ");
        StockSymbol preferred = stock(2L, "00104K", "CJ4우(전환)");
        when(repository.findByMarketAndTicker(MarketType.KRX, "001040")).thenReturn(Optional.empty());
        when(repository.findByMarketAndName(MarketType.KRX, "CJ")).thenReturn(Optional.of(common));

        KrxPreferredStockResolver resolver = new KrxPreferredStockResolver(repository);

        assertThat(resolver.resolveBaseCommonStock(preferred)).isEqualTo(common);
    }

    @Test
    void returnsNullForNonPreferredStocks() {
        StockSymbolRepository repository = mock(StockSymbolRepository.class);
        KrxPreferredStockResolver resolver = new KrxPreferredStockResolver(repository);

        assertThat(resolver.resolveBaseCommonStock(stock(1L, "005930", "삼성전자"))).isNull();
    }

    @Test
    void ignoresPreferredCandidatesWhenFindingBaseStock() {
        StockSymbolRepository repository = mock(StockSymbolRepository.class);
        StockSymbol preferred = stock(2L, "005935", "삼성전자우");
        StockSymbol otherPreferred = stock(3L, "005937", "삼성전자2우B");
        when(repository.findByMarketAndTicker(MarketType.KRX, "005930")).thenReturn(Optional.of(otherPreferred));
        when(repository.findByMarketAndName(MarketType.KRX, "삼성전자")).thenReturn(Optional.empty());
        when(repository.findByMarketAndActiveTrueAndNameStartingWith(MarketType.KRX, "삼성전자"))
                .thenReturn(List.of(otherPreferred));

        KrxPreferredStockResolver resolver = new KrxPreferredStockResolver(repository);

        assertThat(resolver.resolveBaseCommonStock(preferred)).isNull();
    }

    private StockSymbol stock(Long id, String ticker, String name) {
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
