package com.folo.stock;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PriceSnapshotRepository extends JpaRepository<PriceSnapshot, Long> {

    Optional<PriceSnapshot> findByStockSymbolId(Long stockSymbolId);

    List<PriceSnapshot> findAllByStockSymbolIdIn(List<Long> stockSymbolIds);
}
