package com.folo.stock;

import org.springframework.lang.Nullable;

import java.time.LocalDate;
import java.util.List;

record StockEnrichmentSyncRequest(
        @Nullable List<Long> stockSymbolIds,
        @Nullable String mode
) {
}

record StockEnrichmentSyncResponse(
        String scope,
        String mode,
        int requestedCount
) {
}

record KisDividendDebugRequest(
        @Nullable Long stockSymbolId,
        @Nullable String ticker,
        @Nullable LocalDate fromDate,
        @Nullable LocalDate toDate,
        @Nullable String highGb
) {
}

record KisDividendDebugResponse(
        String ticker,
        String savedPath,
        int rowCount,
        List<String> topLevelKeys,
        List<String> firstRowKeys
) {
}
