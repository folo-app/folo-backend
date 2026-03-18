package com.folo.portfolio;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.lang.Nullable;

record PortfolioHoldingItem(
        Long holdingId,
        String ticker,
        String name,
        String market,
        @Nullable BigDecimal quantity,
        @Nullable BigDecimal avgPrice,
        BigDecimal currentPrice,
        @Nullable BigDecimal totalInvested,
        @Nullable BigDecimal totalValue,
        @Nullable BigDecimal returnAmount,
        BigDecimal returnRate,
        BigDecimal weight
) {
}

record PortfolioResponse(
        Long portfolioId,
        @Nullable BigDecimal totalInvested,
        @Nullable BigDecimal totalValue,
        @Nullable BigDecimal totalReturn,
        BigDecimal totalReturnRate,
        @Nullable BigDecimal dayReturn,
        BigDecimal dayReturnRate,
        List<PortfolioHoldingItem> holdings,
        @Nullable String syncedAt,
        boolean isFullyVisible
) {
}
