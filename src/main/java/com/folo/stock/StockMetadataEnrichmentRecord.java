package com.folo.stock;

import org.springframework.lang.Nullable;

public record StockMetadataEnrichmentRecord(
        @Nullable String sectorNameRaw,
        @Nullable String industryNameRaw,
        StockClassificationScheme classificationScheme,
        @Nullable String sourcePayloadVersion
) {

    @Nullable
    public String representativeSectorName() {
        if (sectorNameRaw != null && !sectorNameRaw.isBlank()) {
            return sectorNameRaw.trim();
        }
        if (industryNameRaw != null && !industryNameRaw.isBlank()) {
            return industryNameRaw.trim();
        }
        return null;
    }
}
