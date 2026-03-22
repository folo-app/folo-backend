package com.folo.stock;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StockSymbolEnrichmentRepository extends JpaRepository<StockSymbolEnrichment, Long> {

    Optional<StockSymbolEnrichment> findByStockSymbolIdAndProvider(Long stockSymbolId, StockDataProvider provider);

    Optional<StockSymbolEnrichment> findTopByStockSymbolIdOrderByLastEnrichedAtDesc(Long stockSymbolId);
}
