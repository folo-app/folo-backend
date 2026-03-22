package com.folo.stock;

import com.folo.common.enums.AssetType;
import com.folo.common.enums.MarketType;

public record StockMasterSymbolRecord(
        MarketType market,
        String ticker,
        String name,
        AssetType assetType,
        boolean active,
        String primaryExchangeCode,
        String currencyCode,
        String sourceIdentifier,
        String sectorName,
        java.math.BigDecimal annualDividendYield,
        String dividendMonthsCsv
) {
}
