package com.folo.stock;

import com.folo.common.enums.AssetType;
import com.folo.common.enums.MarketType;
import com.folo.portfolio.Holding;
import com.folo.portfolio.HoldingRepository;
import com.folo.security.FoloUserPrincipal;
import com.folo.trade.TradeRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockRecommendationServiceTest {

    @Mock
    private StockSymbolRepository stockSymbolRepository;

    @Mock
    private HoldingRepository holdingRepository;

    @Mock
    private TradeRepository tradeRepository;

    @InjectMocks
    private StockRecommendationService stockRecommendationService;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void recommendForMarketsUsesPortfolioPreferencesAndFiltersUnsafeEtfs() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new FoloUserPrincipal(1L, "user@example.com"),
                        "token",
                        AuthorityUtils.NO_AUTHORITIES
                )
        );

        StockSymbol samsung = stock(1L, MarketType.KRX, "005930", "삼성전자", AssetType.STOCK, "Technology");
        StockSymbol skHynix = stock(2L, MarketType.KRX, "000660", "SK하이닉스", AssetType.STOCK, "Technology");
        StockSymbol naver = stock(3L, MarketType.KRX, "035420", "NAVER", AssetType.STOCK, "Communication Services");
        StockSymbol inverseEtn = stock(4L, MarketType.KRX, "Q760028", "키움 인버스 2X 반도체TOP10 ETN", AssetType.ETF, "Technology");

        Holding samsungHolding = mock(Holding.class);
        when(samsungHolding.getStockSymbol()).thenReturn(samsung);
        when(samsungHolding.getTotalInvested()).thenReturn(new BigDecimal("700000"));
        when(holdingRepository.findByUserIdOrderByIdAsc(1L)).thenReturn(List.of(samsungHolding));

        when(holdingRepository.findTopRecommendationStatsByMarkets(eq(List.of(MarketType.KRX)), any(Pageable.class)))
                .thenReturn(List.of(
                        new StockPopularityStat(1L, 30L, new BigDecimal("30000000")),
                        new StockPopularityStat(2L, 24L, new BigDecimal("22000000"))
                ));
        when(tradeRepository.findTopRecommendationStatsByMarkets(eq(List.of(MarketType.KRX)), any(Pageable.class)))
                .thenReturn(List.of(
                        new StockPopularityStat(3L, 18L, new BigDecimal("18000000")),
                        new StockPopularityStat(4L, 25L, new BigDecimal("17000000"))
                ));

        when(stockSymbolRepository.findByMarketAndTickerInAndActiveTrue(
                eq(MarketType.KRX),
                org.mockito.ArgumentMatchers.<List<String>>any()
        )).thenReturn(List.of(samsung, skHynix, naver));
        when(stockSymbolRepository.findActiveByMarketsAndSectorNames(
                eq(List.of(MarketType.KRX)),
                eq(List.of("TECHNOLOGY")),
                any(Pageable.class)
        )).thenReturn(List.of(samsung, skHynix));
        when(stockSymbolRepository.findActiveByMarkets(eq(List.of(MarketType.KRX)), any(Pageable.class)))
                .thenReturn(List.of(samsung, skHynix, naver, inverseEtn));
        when(stockSymbolRepository.findAllById(any())).thenReturn(List.of(samsung, skHynix, naver, inverseEtn));

        List<StockSymbol> result = stockRecommendationService.recommendForMarkets(List.of(MarketType.KRX), 3);

        assertEquals(List.of("000660", "035420"), result.stream().map(StockSymbol::getTicker).toList());
    }

    private StockSymbol stock(
            Long id,
            MarketType market,
            String ticker,
            String name,
            AssetType assetType,
            String sectorName
    ) {
        StockSymbol stock = new StockSymbol();
        stock.setId(id);
        stock.setMarket(market);
        stock.setTicker(ticker);
        stock.setName(name);
        stock.setAssetType(assetType);
        stock.setActive(true);
        stock.setSectorName(sectorName);
        return stock;
    }
}
