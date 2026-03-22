package com.folo.stock;

import com.folo.common.enums.MarketType;

import java.time.LocalDate;
import java.util.List;

public interface StockDividendSyncProvider {

    StockDataProvider provider();

    boolean isConfigured();

    boolean supports(MarketType market);

    List<DividendEventRecord> fetchEvents(StockSymbol stockSymbol, LocalDate fromDateInclusive);
}
