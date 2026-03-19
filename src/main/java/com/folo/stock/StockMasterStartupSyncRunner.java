package com.folo.stock;

import com.folo.config.MarketDataSyncProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class StockMasterStartupSyncRunner {

    private final MarketDataSyncProperties properties;
    private final StockMasterSyncService stockMasterSyncService;

    public StockMasterStartupSyncRunner(
            MarketDataSyncProperties properties,
            StockMasterSyncService stockMasterSyncService
    ) {
        this.properties = properties;
        this.stockMasterSyncService = stockMasterSyncService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void syncOnStartup() {
        if (!properties.enabled() || !properties.runOnStartup()) {
            log.info(
                    "stock master startup sync skipped: enabled={}, runOnStartup={}",
                    properties.enabled(),
                    properties.runOnStartup()
            );
            return;
        }

        log.info("stock master startup sync started");
        stockMasterSyncService.syncAll();
        log.info("stock master startup sync completed");
    }
}
