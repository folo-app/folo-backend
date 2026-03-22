package com.folo.stock;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StockDividendEventRepository extends JpaRepository<StockDividendEvent, Long> {

    List<StockDividendEvent> findAllByStockSymbolId(Long stockSymbolId);

    Optional<StockDividendEvent> findByProviderAndDedupeKey(StockDataProvider provider, String dedupeKey);
}
