package com.folo.trade;

import com.folo.common.enums.TradeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TradeRepository extends JpaRepository<Trade, Long> {

    Page<Trade> findByUserIdAndDeletedFalse(Long userId, Pageable pageable);

    Page<Trade> findByUserIdAndTradeTypeAndDeletedFalse(Long userId, TradeType tradeType, Pageable pageable);

    List<Trade> findByUserIdAndDeletedFalseOrderByTradedAtAscIdAsc(Long userId);

    List<Trade> findByUserIdInAndDeletedFalseAndIdLessThanOrderByIdDesc(List<Long> userIds, Long cursor, Pageable pageable);

    List<Trade> findByUserIdInAndDeletedFalseOrderByIdDesc(List<Long> userIds, Pageable pageable);

    List<Trade> findByUserIdAndDeletedFalseAndIdLessThanOrderByIdDesc(Long userId, Long cursor, Pageable pageable);

    List<Trade> findByUserIdAndDeletedFalseOrderByIdDesc(Long userId, Pageable pageable);

    Optional<Trade> findByIdAndDeletedFalse(Long id);

    Page<Trade> findByUserIdAndDeletedFalseAndTradedAtBetween(Long userId, LocalDateTime from, LocalDateTime to, Pageable pageable);
}
