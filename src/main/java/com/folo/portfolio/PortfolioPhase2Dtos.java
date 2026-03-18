package com.folo.portfolio;

import java.util.List;

record PortfolioSyncResponse(
        int syncedHoldings,
        int syncedTrades,
        String syncedAt
) {
}
