package com.folo.stock;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
public class StockSectorBackfillService {

    private static final int DEFAULT_BATCH_SIZE = 500;

    private final StockSymbolRepository stockSymbolRepository;
    private final StockSymbolEnrichmentRepository stockSymbolEnrichmentRepository;
    private final KrxDomesticSectorMapService krxDomesticSectorMapService;

    public StockSectorBackfillService(
            StockSymbolRepository stockSymbolRepository,
            StockSymbolEnrichmentRepository stockSymbolEnrichmentRepository,
            KrxDomesticSectorMapService krxDomesticSectorMapService
    ) {
        this.stockSymbolRepository = stockSymbolRepository;
        this.stockSymbolEnrichmentRepository = stockSymbolEnrichmentRepository;
        this.krxDomesticSectorMapService = krxDomesticSectorMapService;
    }

    @Transactional
    public BackfillResult backfillActiveSymbols(int batchSize) {
        List<StockSymbol> activeSymbols = stockSymbolRepository.findAllByActiveTrueOrderByIdAsc();
        if (activeSymbols.isEmpty()) {
            return new BackfillResult(0, 0);
        }

        int normalizedBatchSize = batchSize > 0 ? batchSize : DEFAULT_BATCH_SIZE;
        int updated = 0;

        for (int start = 0; start < activeSymbols.size(); start += normalizedBatchSize) {
            List<StockSymbol> batch = activeSymbols.subList(start, Math.min(activeSymbols.size(), start + normalizedBatchSize));
            Map<Long, StockSymbolEnrichment> latestEnrichments = latestEnrichmentBySymbolId(batch);

            for (StockSymbol symbol : batch) {
                StockSymbolEnrichment latestEnrichment = latestEnrichments.get(symbol.getId());
                if (applyCanonicalSector(symbol, latestEnrichment)) {
                    stockSymbolRepository.save(symbol);
                    updated++;
                }
            }
        }

        log.info("stock sector backfill completed: processed={}, updated={}", activeSymbols.size(), updated);
        return new BackfillResult(activeSymbols.size(), updated);
    }

    private Map<Long, StockSymbolEnrichment> latestEnrichmentBySymbolId(List<StockSymbol> batch) {
        List<Long> symbolIds = batch.stream()
                .map(StockSymbol::getId)
                .filter(Objects::nonNull)
                .toList();

        if (symbolIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, StockSymbolEnrichment> latestBySymbolId = new LinkedHashMap<>();
        for (StockSymbolEnrichment enrichment : stockSymbolEnrichmentRepository.findLatestCandidatesByStockSymbolIds(symbolIds)) {
            Long symbolId = enrichment.getStockSymbol().getId();
            latestBySymbolId.putIfAbsent(symbolId, enrichment);
        }
        return latestBySymbolId;
    }

    private boolean applyCanonicalSector(StockSymbol symbol, StockSymbolEnrichment latestEnrichment) {
        StockMetadataEnrichmentRecord secondaryRecord = krxDomesticSectorMapService.resolve(symbol);
        String rawSector = secondaryRecord != null
                ? secondaryRecord.sectorNameRaw()
                : latestEnrichment != null ? latestEnrichment.getSectorNameRaw() : null;
        String rawIndustry = secondaryRecord != null
                ? secondaryRecord.industryNameRaw()
                : latestEnrichment != null ? latestEnrichment.getIndustryNameRaw() : null;
        StockClassificationScheme classificationScheme = secondaryRecord != null
                ? secondaryRecord.classificationScheme()
                : latestEnrichment != null ? latestEnrichment.getClassificationScheme() : null;

        StockSectorNormalizer.ResolvedSector resolvedSector = StockSectorNormalizer.resolve(
                symbol.getAssetType(),
                symbol.getSectorCode(),
                symbol.getSectorName(),
                rawSector,
                rawIndustry,
                classificationScheme
        );

        boolean changed = false;
        if (!Objects.equals(symbol.getSectorCode(), resolvedSector.code())) {
            symbol.setSectorCode(resolvedSector.code());
            changed = true;
        }

        if (!Objects.equals(symbol.getSectorName(), resolvedSector.label())) {
            symbol.setSectorName(resolvedSector.label());
            changed = true;
        }

        return changed;
    }

    public record BackfillResult(
            int processedCount,
            int updatedCount
    ) {
    }
}
