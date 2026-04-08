package com.folo.fx;

import com.folo.config.MarketDataSyncProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class FxRateSyncScheduler {

    private final MarketDataSyncProperties properties;
    private final FxRateService fxRateService;

    public FxRateSyncScheduler(
            MarketDataSyncProperties properties,
            FxRateService fxRateService
    ) {
        this.properties = properties;
        this.fxRateService = fxRateService;
    }

    @Scheduled(
            cron = "${integration.market-data.fx-cron:0 15 * * * *}",
            zone = "${integration.market-data.zone:Asia/Seoul}"
    )
    public void syncUsdKrwHourly() {
        if (!properties.enabled()) {
            log.info("fx sync skipped: integration.market-data.enabled=false");
            return;
        }

        fxRateService.syncUsdKrw();
    }
}
