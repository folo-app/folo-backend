package com.folo.stock;

import com.folo.config.MarketDataSyncProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class StockSectorStartupBackfillRunner {

    private final MarketDataSyncProperties properties;
    private final StockSectorBackfillService stockSectorBackfillService;

    public StockSectorStartupBackfillRunner(
            MarketDataSyncProperties properties,
            StockSectorBackfillService stockSectorBackfillService
    ) {
        this.properties = properties;
        this.stockSectorBackfillService = stockSectorBackfillService;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(2)
    public void backfillOnStartup() {
        if (!properties.enabled() || !properties.runOnStartup()) {
            log.info(
                    "stock sector startup backfill skipped: enabled={}, runOnStartup={}",
                    properties.enabled(),
                    properties.runOnStartup()
            );
            return;
        }

        log.info("stock sector startup backfill started");
        try {
            StockSectorBackfillService.BackfillResult result =
                    stockSectorBackfillService.backfillActiveSymbols(properties.batchSize());
            log.info(
                    "stock sector startup backfill completed: processed={}, updated={}",
                    result.processedCount(),
                    result.updatedCount()
            );
        } catch (RuntimeException exception) {
            log.error("stock sector startup backfill failed; continuing application startup", exception);
        }
    }
}
