package com.folo.stock;

import com.folo.common.enums.MarketType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StockSymbolRepository extends JpaRepository<StockSymbol, Long> {

    Optional<StockSymbol> findByMarketAndTicker(MarketType market, String ticker);

    Optional<StockSymbol> findByTickerAndMarket(MarketType market, String ticker);

    List<StockSymbol> findTop20ByNameContainingIgnoreCaseOrTickerContainingIgnoreCase(String nameQuery, String tickerQuery);

    List<StockSymbol> findTop20ByMarketAndNameContainingIgnoreCaseOrMarketAndTickerContainingIgnoreCase(
            MarketType market1, String nameQuery, MarketType market2, String tickerQuery
    );
}
