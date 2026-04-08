package com.folo.stock;

import com.folo.common.enums.MarketType;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class StockMetadataEnrichmentService {

    private final List<StockMetadataEnrichmentProvider> providers;
    private final StockSymbolRepository stockSymbolRepository;
    private final StockSymbolEnrichmentRepository stockSymbolEnrichmentRepository;
    private final StockSymbolSyncRunRepository stockSymbolSyncRunRepository;
    private final StockEnrichmentTargetSelector stockEnrichmentTargetSelector;

    public StockMetadataEnrichmentService(
            List<StockMetadataEnrichmentProvider> providers,
            StockSymbolRepository stockSymbolRepository,
            StockSymbolEnrichmentRepository stockSymbolEnrichmentRepository,
            StockSymbolSyncRunRepository stockSymbolSyncRunRepository,
            StockEnrichmentTargetSelector stockEnrichmentTargetSelector
    ) {
        this.providers = providers;
        this.stockSymbolRepository = stockSymbolRepository;
        this.stockSymbolEnrichmentRepository = stockSymbolEnrichmentRepository;
        this.stockSymbolSyncRunRepository = stockSymbolSyncRunRepository;
        this.stockEnrichmentTargetSelector = stockEnrichmentTargetSelector;
    }

    public void syncPrioritySymbols() {
        for (StockMetadataEnrichmentProvider provider : providers) {
            List<StockSymbol> symbols = stockEnrichmentTargetSelector.resolvePrioritySymbols(resolveSupportedMarkets(provider));
            syncSymbols(provider, symbols);
        }
    }

    public void syncSymbols(Collection<Long> stockSymbolIds) {
        if (stockSymbolIds == null || stockSymbolIds.isEmpty()) {
            return;
        }

        Map<Long, StockSymbol> symbolsById = stockSymbolRepository.findAllById(stockSymbolIds).stream()
                .collect(Collectors.toMap(StockSymbol::getId, symbol -> symbol, (left, right) -> left, LinkedHashMap::new));

        for (StockMetadataEnrichmentProvider provider : providers) {
            List<StockSymbol> targets = stockSymbolIds.stream()
                    .map(symbolsById::get)
                    .filter(symbol -> symbol != null && provider.supports(symbol.getMarket()))
                    .toList();
            syncSymbols(provider, targets);
        }
    }

    private void syncSymbols(StockMetadataEnrichmentProvider provider, List<StockSymbol> symbols) {
        if (symbols.isEmpty()) {
            return;
        }

        Map<MarketType, List<StockSymbol>> symbolsByMarket = new EnumMap<>(MarketType.class);
        for (StockSymbol symbol : symbols) {
            symbolsByMarket.computeIfAbsent(symbol.getMarket(), ignored -> new ArrayList<>()).add(symbol);
        }

        for (Map.Entry<MarketType, List<StockSymbol>> entry : symbolsByMarket.entrySet()) {
            syncMarket(provider, entry.getKey(), entry.getValue());
        }
    }

    private void syncMarket(StockMetadataEnrichmentProvider provider, MarketType market, List<StockSymbol> symbols) {
        StockSymbolSyncRun syncRun = new StockSymbolSyncRun();
        syncRun.setProvider(provider.provider());
        syncRun.setMarket(market);
        syncRun.setSyncScope(StockSymbolSyncScope.ENRICHMENT);
        syncRun.setStatus(StockSymbolSyncStatus.STARTED);
        syncRun.setStartedAt(LocalDateTime.now());
        stockSymbolSyncRunRepository.save(syncRun);

        if (!provider.isConfigured()) {
            syncRun.setStatus(StockSymbolSyncStatus.SKIPPED);
            syncRun.setCompletedAt(LocalDateTime.now());
            syncRun.setErrorMessage("provider is not configured");
            stockSymbolSyncRunRepository.save(syncRun);
            return;
        }

        int fetchedCount = 0;
        int upsertedCount = 0;
        int failureCount = 0;
        List<String> failures = new ArrayList<>();

        for (StockSymbol symbol : symbols) {
            try {
                StockMetadataEnrichmentRecord record = provider.fetchMetadata(symbol);
                fetchedCount++;
                upsertedCount += upsertMetadata(symbol, provider.provider(), record);
            } catch (RuntimeException exception) {
                failureCount++;
                if (failures.size() < 5) {
                    failures.add(symbol.getTicker() + ": " + exception.getMessage());
                }
            }
        }

        syncRun.setFetchedCount(fetchedCount);
        syncRun.setUpsertedCount(upsertedCount);
        syncRun.setDeactivatedCount(0);
        syncRun.setCompletedAt(LocalDateTime.now());
        if (failureCount > 0) {
            syncRun.setStatus(StockSymbolSyncStatus.FAILED);
            syncRun.setErrorMessage("failed symbols=%d; %s".formatted(failureCount, String.join(" | ", failures)));
        } else {
            syncRun.setStatus(StockSymbolSyncStatus.COMPLETED);
        }
        stockSymbolSyncRunRepository.save(syncRun);
    }

    private int upsertMetadata(
            StockSymbol stockSymbol,
            StockDataProvider provider,
            StockMetadataEnrichmentRecord record
    ) {
        StockSymbolEnrichment entity = stockSymbolEnrichmentRepository.findByStockSymbolIdAndProvider(stockSymbol.getId(), provider)
                .orElseGet(StockSymbolEnrichment::new);

        entity.setStockSymbol(stockSymbol);
        entity.setProvider(provider);
        entity.setSectorNameRaw(record.sectorNameRaw());
        entity.setIndustryNameRaw(record.industryNameRaw());
        entity.setClassificationScheme(record.classificationScheme());
        entity.setSourcePayloadVersion(record.sourcePayloadVersion());
        entity.setLastEnrichedAt(LocalDateTime.now());
        stockSymbolEnrichmentRepository.save(entity);

        StockSectorCode representativeSectorCode = StockSectorNormalizer.normalizeSectorCodeForMetadata(
                record.sectorNameRaw(),
                record.industryNameRaw(),
                record.classificationScheme()
        );
        if (representativeSectorCode != null) {
            stockSymbol.setSectorCode(representativeSectorCode);
            stockSymbol.setSectorName(StockSectorNormalizer.displayLabel(representativeSectorCode));
            stockSymbolRepository.save(stockSymbol);
        }
        return 1;
    }

    private List<MarketType> resolveSupportedMarkets(StockMetadataEnrichmentProvider provider) {
        return List.of(MarketType.NASDAQ, MarketType.NYSE, MarketType.AMEX, MarketType.KRX).stream()
                .filter(provider::supports)
                .toList();
    }
}
