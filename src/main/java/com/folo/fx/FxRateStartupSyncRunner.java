package com.folo.fx;

import com.folo.config.MarketDataSyncProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class FxRateStartupSyncRunner {

    private final MarketDataSyncProperties properties;
    private final FxRateService fxRateService;

    public FxRateStartupSyncRunner(
            MarketDataSyncProperties properties,
            FxRateService fxRateService
    ) {
        this.properties = properties;
        this.fxRateService = fxRateService;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(3)
    public void syncOnStartup() {
        if (!properties.enabled() || !properties.runOnStartup()) {
            log.info(
                    "fx startup sync skipped: enabled={}, runOnStartup={}",
                    properties.enabled(),
                    properties.runOnStartup()
            );
            return;
        }

        log.info("fx startup sync started");
        try {
            fxRateService.syncUsdKrw();
            log.info("fx startup sync completed");
        } catch (RuntimeException exception) {
            log.error("fx startup sync failed; continuing application startup", exception);
        }
    }
}
