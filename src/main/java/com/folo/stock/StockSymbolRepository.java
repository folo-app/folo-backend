package com.folo.stock;

import com.folo.common.enums.MarketType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface StockSymbolRepository extends JpaRepository<StockSymbol, Long> {

    List<StockSymbol> findAllByActiveTrueOrderByIdAsc();

    Optional<StockSymbol> findByMarketAndTicker(MarketType market, String ticker);

    Optional<StockSymbol> findByTickerAndMarket(MarketType market, String ticker);

    Optional<StockSymbol> findByMarketAndName(MarketType market, String name);

    List<StockSymbol> findByMarketAndActiveTrueAndNameStartingWith(MarketType market, String namePrefix);

    @Query("""
            select s
            from StockSymbol s
            where s.active = true
              and s.market in :markets
              and upper(s.ticker) in :tickers
            """)
    List<StockSymbol> findActiveByMarketsAndTickers(List<MarketType> markets, List<String> tickers);

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

    List<StockSymbol> findByMarketAndTickerInAndActiveTrue(MarketType market, List<String> tickers);

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

    @Query("""
            select s
            from StockSymbol s
            where s.active = true
              and s.market = :market
              and s.assetType = com.folo.common.enums.AssetType.STOCK
            order by case when s.lastMasterSyncedAt is null then 1 else 0 end asc,
                     s.lastMasterSyncedAt desc,
                     s.createdAt desc,
                     s.name asc
            """)
    List<StockSymbol> findActiveStocksByMarket(MarketType market, Pageable pageable);

    @Query("""
            select s
            from StockSymbol s
            where s.active = true
              and s.market = :market
              and s.assetType = com.folo.common.enums.AssetType.STOCK
              and not exists (
                    select 1
                    from StockIssuerProfile p
                    where p.stockSymbol = s
                      and p.provider = :provider
              )
            order by s.id asc
            """)
    List<StockSymbol> findActiveStocksByMarketWithoutIssuerProfile(MarketType market, StockDataProvider provider);

    @Query("""
            select s
            from StockSymbol s
            where s.active = true
              and s.market in :markets
              and s.sectorName is not null
              and upper(s.sectorName) in :sectorNames
            order by case when s.assetType = com.folo.common.enums.AssetType.STOCK then 0 else 1 end,
                     s.name asc
            """)
    List<StockSymbol> findActiveByMarketsAndSectorNames(
            List<MarketType> markets,
            List<String> sectorNames,
            Pageable pageable
    );
}
