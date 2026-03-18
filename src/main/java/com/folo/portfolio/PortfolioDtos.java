package com.folo.portfolio;

import java.math.BigDecimal;
import java.util.List;

record PortfolioHoldingItem(
        Long holdingId,
        String ticker,
        String name,
        String market,
        BigDecimal quantity,
        BigDecimal avgPrice,
        BigDecimal currentPrice,
        BigDecimal totalInvested,
        BigDecimal totalValue,
        BigDecimal returnAmount,
        BigDecimal returnRate,
        BigDecimal weight
) {
}

record PortfolioResponse(
        Long portfolioId,
        BigDecimal totalInvested,
        BigDecimal totalValue,
        BigDecimal totalReturn,
        BigDecimal totalReturnRate,
        BigDecimal dayReturn,
        BigDecimal dayReturnRate,
        List<PortfolioHoldingItem> holdings,
        String syncedAt,
        Boolean isFullyVisible
) {
}
