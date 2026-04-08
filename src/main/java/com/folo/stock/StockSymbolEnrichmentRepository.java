package com.folo.stock;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StockSymbolEnrichmentRepository extends JpaRepository<StockSymbolEnrichment, Long> {

    Optional<StockSymbolEnrichment> findByStockSymbolIdAndProvider(Long stockSymbolId, StockDataProvider provider);

    Optional<StockSymbolEnrichment> findTopByStockSymbolIdOrderByLastEnrichedAtDesc(Long stockSymbolId);

    @Query("""
            select enrichment
            from StockSymbolEnrichment enrichment
            where enrichment.stockSymbol.id in :stockSymbolIds
            order by enrichment.stockSymbol.id asc, enrichment.lastEnrichedAt desc
            """)
    List<StockSymbolEnrichment> findLatestCandidatesByStockSymbolIds(@Param("stockSymbolIds") List<Long> stockSymbolIds);
}
