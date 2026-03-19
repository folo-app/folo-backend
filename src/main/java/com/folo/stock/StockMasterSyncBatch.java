package com.folo.stock;

import java.util.List;

public record StockMasterSyncBatch(
        List<StockMasterSymbolRecord> records,
        String nextCursor
) {
}
