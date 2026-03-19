package com.folo.stock;

import com.folo.config.MarketDataSyncProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class StockMasterSyncScheduler {

    private final MarketDataSyncProperties properties;
    private final StockMasterSyncService stockMasterSyncService;

    public StockMasterSyncScheduler(
            MarketDataSyncProperties properties,
            StockMasterSyncService stockMasterSyncService
    ) {
        this.properties = properties;
        this.stockMasterSyncService = stockMasterSyncService;
    }

    @Scheduled(
            cron = "${integration.market-data.master-cron:0 0 4 * * *}",
            zone = "${integration.market-data.zone:Asia/Seoul}"
    )
    public void syncNightly() {
        if (!properties.enabled()) {
            log.info("stock master sync skipped: integration.market-data.enabled=false");
            return;
        }

        stockMasterSyncService.syncAll();
    }
}
