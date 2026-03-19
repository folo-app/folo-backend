package com.folo.stock;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StockSymbolSyncRunRepository extends JpaRepository<StockSymbolSyncRun, Long> {
}
