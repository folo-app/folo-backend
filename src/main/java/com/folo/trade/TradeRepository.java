package com.folo.trade;

import com.folo.common.enums.MarketType;
import com.folo.common.enums.TradeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TradeRepository extends JpaRepository<Trade, Long>, JpaSpecificationExecutor<Trade> {

    Page<Trade> findByUserIdAndDeletedFalse(Long userId, Pageable pageable);

    Page<Trade> findByUserIdAndTradeTypeAndDeletedFalse(Long userId, TradeType tradeType, Pageable pageable);

    List<Trade> findByUserIdAndDeletedFalseOrderByTradedAtAscIdAsc(Long userId);

    List<Trade> findByUserIdInAndDeletedFalseAndIdLessThanOrderByIdDesc(List<Long> userIds, Long cursor, Pageable pageable);

    List<Trade> findByUserIdInAndDeletedFalseOrderByIdDesc(List<Long> userIds, Pageable pageable);

    List<Trade> findByUserIdAndDeletedFalseAndIdLessThanOrderByIdDesc(Long userId, Long cursor, Pageable pageable);

    List<Trade> findByUserIdAndDeletedFalseOrderByIdDesc(Long userId, Pageable pageable);

    Optional<Trade> findByIdAndDeletedFalse(Long id);

    Page<Trade> findByUserIdAndDeletedFalseAndTradedAtBetween(Long userId, LocalDateTime from, LocalDateTime to, Pageable pageable);

    @Query("""
            select t.stockSymbol.id
            from Trade t
            where t.deleted = false
              and t.stockSymbol.market in :markets
            group by t.stockSymbol.id
            order by count(t.id) desc, sum(t.totalAmount) desc
            """)
    List<Long> findTopSymbolIdsByMarkets(@Param("markets") List<MarketType> markets);
}
