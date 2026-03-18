package com.folo.importer;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.lang.Nullable;

record ImportPreviewItem(
        Long importResultId,
        @Nullable String ticker,
        @Nullable String name,
        @Nullable String market,
        @Nullable String tradeType,
        @Nullable BigDecimal quantity,
        @Nullable BigDecimal price,
        @Nullable String tradedAt,
        boolean valid,
        @Nullable String errorMessage,
        boolean selected
) {
}

record CsvImportResponse(
        Long importJobId,
        int parsedTrades,
        int failedTrades,
        List<ImportPreviewItem> preview
) {
}

record ConfirmImportRequest(
        @Nullable List<Long> importResultIds
) {
}

record ConfirmImportResponse(
        int savedTrades,
        List<Long> confirmedImportResultIds,
        List<Long> tradeIds
) {
}

record OcrImportParsedTrade(
        Long importResultId,
        String ticker,
        String name,
        String tradeType,
        BigDecimal quantity,
        BigDecimal price,
        String tradedAt
) {
}

record OcrImportResponse(
        Long importJobId,
        @Nullable OcrImportParsedTrade parsed,
        double confidence
) {
}
