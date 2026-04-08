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
        BigDecimal weight,
        @Nullable String sectorCode,
        @Nullable String sectorName,
        String assetType,
        @Nullable String currencyCode,
        @Nullable BigDecimal annualDividendYield,
        List<Integer> dividendMonths,
        @Nullable BigDecimal displayTotalInvested,
        @Nullable BigDecimal displayTotalValue,
        @Nullable BigDecimal displayReturnAmount
) {
}

record PortfolioAllocationItem(
        String key,
        String label,
        BigDecimal weight,
        @Nullable BigDecimal value
) {
}

record PortfolioMonthlyDividendItem(
        int month,
        String label,
        BigDecimal amount
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
        List<PortfolioAllocationItem> sectorAllocations,
        List<PortfolioMonthlyDividendItem> monthlyDividendForecasts,
        @Nullable BigDecimal cashValue,
        BigDecimal cashWeight,
        @Nullable String syncedAt,
        String displayCurrency,
        @Nullable String fxAsOf,
        boolean fxStale,
        boolean isFullyVisible
) {
}
