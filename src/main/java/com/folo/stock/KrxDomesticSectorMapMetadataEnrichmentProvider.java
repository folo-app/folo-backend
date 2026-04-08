package com.folo.stock;

import com.folo.common.enums.MarketType;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Order(20)
@Component
public class KrxDomesticSectorMapMetadataEnrichmentProvider implements StockMetadataEnrichmentProvider {

    private final KrxDomesticSectorMapService sectorMapService;

    public KrxDomesticSectorMapMetadataEnrichmentProvider(KrxDomesticSectorMapService sectorMapService) {
        this.sectorMapService = sectorMapService;
    }

    @Override
    public StockDataProvider provider() {
        return StockDataProvider.KRX_SECTOR_MAP;
    }

    @Override
    public boolean isConfigured() {
        return sectorMapService.isConfigured();
    }

    @Override
    public boolean supports(MarketType market) {
        return market == MarketType.KRX;
    }

    @Override
    public StockMetadataEnrichmentRecord fetchMetadata(StockSymbol stockSymbol) {
        StockMetadataEnrichmentRecord record = sectorMapService.resolve(stockSymbol);
        if (record != null) {
            return record;
        }

        return new StockMetadataEnrichmentRecord(
                null,
                null,
                StockClassificationScheme.KRX_SECTOR_MAP,
                "krx:sector-map:v1"
        );
    }
}
