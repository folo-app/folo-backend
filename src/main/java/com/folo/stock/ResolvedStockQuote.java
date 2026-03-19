package com.folo.stock;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ResolvedStockQuote(
        StockSymbol stockSymbol,
        BigDecimal currentPrice,
        BigDecimal openPrice,
        BigDecimal highPrice,
        BigDecimal lowPrice,
        BigDecimal dayReturn,
        BigDecimal dayReturnRate,
        LocalDateTime marketUpdatedAt
) {
}
