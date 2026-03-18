package com.folo.importer;

import java.math.BigDecimal;
import java.util.List;

record ImportPreviewItem(
        Long importResultId,
        String ticker,
        String name,
        String market,
        String tradeType,
        BigDecimal quantity,
        BigDecimal price,
        String tradedAt,
        boolean valid,
        String errorMessage,
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
        List<Long> importResultIds
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
        OcrImportParsedTrade parsed,
        double confidence
) {
}
