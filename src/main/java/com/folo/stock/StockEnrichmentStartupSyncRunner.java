package com.folo.stock;

import com.folo.config.MarketDataSyncProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class StockEnrichmentStartupSyncRunner {

    private final MarketDataSyncProperties properties;
    private final StockDividendEnrichmentService stockDividendEnrichmentService;
    private final StockMetadataEnrichmentService stockMetadataEnrichmentService;

    public StockEnrichmentStartupSyncRunner(
            MarketDataSyncProperties properties,
            StockDividendEnrichmentService stockDividendEnrichmentService,
            StockMetadataEnrichmentService stockMetadataEnrichmentService
    ) {
        this.properties = properties;
        this.stockDividendEnrichmentService = stockDividendEnrichmentService;
        this.stockMetadataEnrichmentService = stockMetadataEnrichmentService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void syncOnStartup() {
        if (!properties.enabled() || !properties.runOnStartup()) {
            log.info(
                    "stock enrichment startup sync skipped: enabled={}, runOnStartup={}",
                    properties.enabled(),
                    properties.runOnStartup()
            );
            return;
        }

        log.info("stock metadata enrichment startup sync started");
        try {
            stockMetadataEnrichmentService.syncPrioritySymbols();
            log.info("stock metadata enrichment startup sync completed");
        } catch (RuntimeException exception) {
            log.error("stock metadata enrichment startup sync failed; continuing application startup", exception);
        }

        log.info("stock dividend enrichment startup sync started");
        try {
            stockDividendEnrichmentService.syncPrioritySymbols();
            log.info("stock dividend enrichment startup sync completed");
        } catch (RuntimeException exception) {
            log.error("stock dividend enrichment startup sync failed; continuing application startup", exception);
        }
    }
}
