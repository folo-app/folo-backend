package com.folo.stock;

import java.math.BigDecimal;

public record StockPopularityStat(
        Long stockSymbolId,
        Long popularityCount,
        BigDecimal popularityAmount
) {
}
