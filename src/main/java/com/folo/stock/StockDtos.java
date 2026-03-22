package com.folo.stock;

import com.folo.common.enums.MarketType;

import java.math.BigDecimal;
import java.util.List;

record StockSearchItem(
        String ticker,
        String name,
        MarketType market,
        String logoUrl,
        BigDecimal currentPrice,
        BigDecimal dayReturnRate,
        String sectorName
) {
}

record StockSearchResponse(
        List<StockSearchItem> stocks
) {
}

record StockDiscoverResponse(
        List<StockSearchItem> krxStocks,
        List<StockSearchItem> usStocks
) {
}

record StockPriceResponse(
        String ticker,
        String name,
        MarketType market,
        BigDecimal currentPrice,
        BigDecimal openPrice,
        BigDecimal highPrice,
        BigDecimal lowPrice,
        BigDecimal dayReturn,
        BigDecimal dayReturnRate,
        String sectorName,
        String industryName,
        String classificationScheme,
        String updatedAt
) {
}
