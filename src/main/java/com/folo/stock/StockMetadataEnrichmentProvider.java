package com.folo.stock;

import com.folo.common.enums.MarketType;

public interface StockMetadataEnrichmentProvider {

    StockDataProvider provider();

    boolean isConfigured();

    boolean supports(MarketType market);

    StockMetadataEnrichmentRecord fetchMetadata(StockSymbol stockSymbol);
}
