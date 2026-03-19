package com.folo.stock;

import com.folo.common.enums.MarketType;

public interface StockMasterSyncProvider {

    StockDataProvider provider();

    boolean isConfigured();

    boolean supports(MarketType market);

    StockMasterSyncBatch fetchBatch(MarketType market, String cursor, int batchSize);
}
