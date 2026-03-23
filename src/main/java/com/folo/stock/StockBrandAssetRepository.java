package com.folo.stock;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StockBrandAssetRepository extends JpaRepository<StockBrandAsset, Long> {

    Optional<StockBrandAsset> findByStockSymbolId(Long stockSymbolId);

    List<StockBrandAsset> findAllByStockSymbolIdIn(List<Long> stockSymbolIds);
}
