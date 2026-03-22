package com.folo.stock;

import com.folo.config.MarketDataSyncProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class StockEnrichmentSyncScheduler {

    private final MarketDataSyncProperties properties;
    private final StockDividendEnrichmentService stockDividendEnrichmentService;
    private final StockMetadataEnrichmentService stockMetadataEnrichmentService;

    public StockEnrichmentSyncScheduler(
            MarketDataSyncProperties properties,
            StockDividendEnrichmentService stockDividendEnrichmentService,
            StockMetadataEnrichmentService stockMetadataEnrichmentService
    ) {
        this.properties = properties;
        this.stockDividendEnrichmentService = stockDividendEnrichmentService;
        this.stockMetadataEnrichmentService = stockMetadataEnrichmentService;
    }

    @Scheduled(
            cron = "${integration.market-data.dividend-cron:0 30 4 * * *}",
            zone = "${integration.market-data.zone:Asia/Seoul}"
    )
    public void syncDividendNightly() {
        if (!properties.enabled()) {
            log.info("stock dividend enrichment sync skipped: integration.market-data.enabled=false");
            return;
        }

        stockDividendEnrichmentService.syncPrioritySymbols();
    }

    @Scheduled(
            cron = "${integration.market-data.metadata-cron:0 0 5 * * *}",
            zone = "${integration.market-data.zone:Asia/Seoul}"
    )
    public void syncMetadataNightly() {
        if (!properties.enabled()) {
            log.info("stock metadata enrichment sync skipped: integration.market-data.enabled=false");
            return;
        }

        stockMetadataEnrichmentService.syncPrioritySymbols();
    }
}
