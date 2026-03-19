package com.folo.portfolio;

import com.folo.common.enums.MarketType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface HoldingRepository extends JpaRepository<Holding, Long> {

    List<Holding> findByUserIdOrderByIdAsc(Long userId);

    Optional<Holding> findByUserIdAndStockSymbolId(Long userId, Long stockSymbolId);

    @Query("""
            select h.stockSymbol.id
            from Holding h
            where h.stockSymbol.market in :markets
            group by h.stockSymbol.id
            order by count(h.id) desc, sum(h.totalInvested) desc
            """)
    List<Long> findTopSymbolIdsByMarkets(@Param("markets") List<MarketType> markets);
}
