package com.folo.stock;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StockIssuerProfileRepository extends JpaRepository<StockIssuerProfile, Long> {

    Optional<StockIssuerProfile> findByStockSymbolIdAndProvider(Long stockSymbolId, StockDataProvider provider);

    Optional<StockIssuerProfile> findByProviderAndCorpCode(StockDataProvider provider, String corpCode);

    List<StockIssuerProfile> findAllByStockSymbolIdIn(List<Long> stockSymbolIds);
}
