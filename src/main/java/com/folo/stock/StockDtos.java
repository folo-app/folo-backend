package com.folo.stock;

import com.folo.common.enums.MarketType;

import java.math.BigDecimal;
import java.util.List;

record StockSearchItem(
        String ticker,
        String name,
        MarketType market,
        BigDecimal currentPrice,
        BigDecimal dayReturnRate
) {
}

record StockSearchResponse(
        List<StockSearchItem> stocks
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
        String updatedAt
) {
}
