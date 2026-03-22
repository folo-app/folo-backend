package com.folo.stock;

import com.folo.common.enums.MarketType;
import com.folo.config.MarketDataSyncProperties;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class StockDividendEnrichmentService {

    private static final int LOOKBACK_MONTHS = 36;

    private final MarketDataSyncProperties properties;
    private final List<StockDividendSyncProvider> providers;
    private final StockSymbolRepository stockSymbolRepository;
    private final StockDividendEventRepository stockDividendEventRepository;
    private final DividendAnalyticsService dividendAnalyticsService;
    private final StockSymbolSyncRunRepository stockSymbolSyncRunRepository;
    private final StockEnrichmentTargetSelector stockEnrichmentTargetSelector;

    public StockDividendEnrichmentService(
            MarketDataSyncProperties properties,
            List<StockDividendSyncProvider> providers,
            StockSymbolRepository stockSymbolRepository,
            StockDividendEventRepository stockDividendEventRepository,
            DividendAnalyticsService dividendAnalyticsService,
            StockSymbolSyncRunRepository stockSymbolSyncRunRepository,
            StockEnrichmentTargetSelector stockEnrichmentTargetSelector
    ) {
        this.properties = properties;
        this.providers = providers;
        this.stockSymbolRepository = stockSymbolRepository;
        this.stockDividendEventRepository = stockDividendEventRepository;
        this.dividendAnalyticsService = dividendAnalyticsService;
        this.stockSymbolSyncRunRepository = stockSymbolSyncRunRepository;
        this.stockEnrichmentTargetSelector = stockEnrichmentTargetSelector;
    }

    public void syncPrioritySymbols() {
        for (StockDividendSyncProvider provider : providers) {
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

        for (StockDividendSyncProvider provider : providers) {
            List<StockSymbol> targets = stockSymbolIds.stream()
                    .map(symbolsById::get)
                    .filter(symbol -> symbol != null && provider.supports(symbol.getMarket()))
                    .toList();
            syncSymbols(provider, targets);
        }
    }

    private void syncSymbols(StockDividendSyncProvider provider, List<StockSymbol> symbols) {
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

    private void syncMarket(StockDividendSyncProvider provider, MarketType market, List<StockSymbol> symbols) {
        StockSymbolSyncRun syncRun = new StockSymbolSyncRun();
        syncRun.setProvider(provider.provider());
        syncRun.setMarket(market);
        syncRun.setSyncScope(StockSymbolSyncScope.DIVIDEND);
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
                List<DividendEventRecord> events = provider.fetchEvents(symbol, LocalDate.now().minusMonths(LOOKBACK_MONTHS));
                fetchedCount += events.size();
                upsertedCount += upsertEvents(symbol, provider.provider(), events);
                dividendAnalyticsService.refreshAnalytics(symbol.getId());
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

    private int upsertEvents(
            StockSymbol stockSymbol,
            StockDataProvider provider,
            List<DividendEventRecord> events
    ) {
        int changed = 0;

        for (DividendEventRecord event : events) {
            String dedupeKey = event.dedupeKey(stockSymbol.getId());
            StockDividendEvent entity = stockDividendEventRepository.findByProviderAndDedupeKey(provider, dedupeKey)
                    .orElseGet(StockDividendEvent::new);

            entity.setStockSymbol(stockSymbol);
            entity.setProvider(provider);
            entity.setSourceEventId(event.sourceEventId());
            entity.setDedupeKey(dedupeKey);
            entity.setEventType(event.eventType());
            entity.setDeclaredDate(event.declaredDate());
            entity.setExDividendDate(event.exDividendDate());
            entity.setRecordDate(event.recordDate());
            entity.setPayDate(event.payDate());
            entity.setCashAmount(event.cashAmount());
            entity.setCurrencyCode(event.currencyCode());
            entity.setFrequencyRaw(event.frequencyRaw());
            stockDividendEventRepository.save(entity);
            changed++;
        }

        return changed;
    }

    private List<MarketType> resolveSupportedMarkets(StockDividendSyncProvider provider) {
        return List.of(MarketType.NASDAQ, MarketType.NYSE, MarketType.AMEX, MarketType.KRX).stream()
                .filter(provider::supports)
                .toList();
    }
}
