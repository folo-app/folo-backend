package com.folo.stock;

import com.folo.common.enums.MarketType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface StockSymbolRepository extends JpaRepository<StockSymbol, Long> {

    Optional<StockSymbol> findByMarketAndTicker(MarketType market, String ticker);

    Optional<StockSymbol> findByTickerAndMarket(MarketType market, String ticker);

    @Query("""
            select s
            from StockSymbol s
            where s.active = true
              and s.market in :markets
              and (
                lower(s.name) like lower(concat('%', :query, '%'))
                or lower(s.ticker) like lower(concat('%', :query, '%'))
              )
            order by case when upper(s.ticker) = upper(:query) then 0 else 1 end,
                     case when lower(s.name) = lower(:query) then 0 else 1 end,
                     s.name asc
            """)
    List<StockSymbol> searchTopByMarkets(List<MarketType> markets, String query, Pageable pageable);

    List<StockSymbol> findAllBySourceProviderAndMarket(StockDataProvider sourceProvider, MarketType market);

    @Query("""
            select s
            from StockSymbol s
            where s.active = true
              and s.market in :markets
            order by case when s.lastMasterSyncedAt is null then 1 else 0 end asc,
                     s.lastMasterSyncedAt desc,
                     s.createdAt desc,
                     s.name asc
            """)
    List<StockSymbol> findActiveByMarkets(List<MarketType> markets, Pageable pageable);
}
