package com.folo.stock;

import com.folo.common.enums.AssetType;
import com.folo.common.enums.MarketType;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class StockIssuerProfileSyncService {

    private final OpendartClient opendartClient;
    private final StockSymbolRepository stockSymbolRepository;
    private final StockIssuerProfileRepository stockIssuerProfileRepository;
    private final StockSymbolSyncRunRepository stockSymbolSyncRunRepository;
    private final StockEnrichmentTargetSelector stockEnrichmentTargetSelector;

    public StockIssuerProfileSyncService(
            OpendartClient opendartClient,
            StockSymbolRepository stockSymbolRepository,
            StockIssuerProfileRepository stockIssuerProfileRepository,
            StockSymbolSyncRunRepository stockSymbolSyncRunRepository,
            StockEnrichmentTargetSelector stockEnrichmentTargetSelector
    ) {
        this.opendartClient = opendartClient;
        this.stockSymbolRepository = stockSymbolRepository;
        this.stockIssuerProfileRepository = stockIssuerProfileRepository;
        this.stockSymbolSyncRunRepository = stockSymbolSyncRunRepository;
        this.stockEnrichmentTargetSelector = stockEnrichmentTargetSelector;
    }

    public void syncPrioritySymbols() {
        List<StockSymbol> targets = stockEnrichmentTargetSelector.resolvePrioritySymbols(List.of(MarketType.KRX)).stream()
                .filter(this::isIssuerProfileTarget)
                .toList();
        if (targets.isEmpty()) {
            targets = stockSymbolRepository.findActiveStocksByMarket(MarketType.KRX, PageRequest.of(0, 100));
        }
        syncSymbolsInternal(targets);
    }

    public void syncSymbols(Collection<Long> stockSymbolIds) {
        if (stockSymbolIds == null || stockSymbolIds.isEmpty()) {
            return;
        }

        Map<Long, StockSymbol> symbolsById = stockSymbolRepository.findAllById(stockSymbolIds).stream()
                .filter(this::isIssuerProfileTarget)
                .collect(Collectors.toMap(StockSymbol::getId, symbol -> symbol, (left, right) -> left, LinkedHashMap::new));

        syncSymbolsInternal(stockSymbolIds.stream()
                .map(symbolsById::get)
                .filter(symbol -> symbol != null)
                .toList());
    }

    private void syncSymbolsInternal(List<StockSymbol> symbols) {
        if (symbols.isEmpty()) {
            return;
        }

        StockSymbolSyncRun syncRun = new StockSymbolSyncRun();
        syncRun.setProvider(StockDataProvider.OPENDART);
        syncRun.setMarket(MarketType.KRX);
        syncRun.setSyncScope(StockSymbolSyncScope.ISSUER_PROFILE);
        syncRun.setStatus(StockSymbolSyncStatus.STARTED);
        syncRun.setStartedAt(LocalDateTime.now());
        stockSymbolSyncRunRepository.save(syncRun);

        if (!opendartClient.isConfigured()) {
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

        try {
            Map<String, OpendartCorpCodeRecord> corpCodesByStockCode = opendartClient.fetchCorpCodes();

            for (StockSymbol symbol : symbols) {
                try {
                    OpendartCorpCodeRecord corpCode = corpCodesByStockCode.get(symbol.getTicker());
                    if (corpCode == null) {
                        continue;
                    }

                    OpendartCompanyRecord company = opendartClient.fetchCompany(corpCode.corpCode());
                    if (company == null) {
                        continue;
                    }

                    fetchedCount++;
                    upsertedCount += upsertProfile(symbol, corpCode, company);
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
        } catch (RuntimeException exception) {
            syncRun.setFetchedCount(fetchedCount);
            syncRun.setUpsertedCount(upsertedCount);
            syncRun.setDeactivatedCount(0);
            syncRun.setStatus(StockSymbolSyncStatus.FAILED);
            syncRun.setCompletedAt(LocalDateTime.now());
            syncRun.setErrorMessage(exception.getMessage());
            stockSymbolSyncRunRepository.save(syncRun);
            throw exception;
        }
    }

    private int upsertProfile(
            StockSymbol stockSymbol,
            OpendartCorpCodeRecord corpCode,
            OpendartCompanyRecord company
    ) {
        StockIssuerProfile profile = stockIssuerProfileRepository.findByStockSymbolIdAndProvider(
                stockSymbol.getId(),
                StockDataProvider.OPENDART
        ).orElseGet(StockIssuerProfile::new);

        profile.setStockSymbol(stockSymbol);
        profile.setProvider(StockDataProvider.OPENDART);
        profile.setCorpCode(corpCode.corpCode());
        profile.setCorpName(firstNonBlank(company.corpName(), corpCode.corpName(), stockSymbol.getName()));
        profile.setStockCode(firstNonBlank(company.stockCode(), corpCode.stockCode(), stockSymbol.getTicker()));
        profile.setCorpCls(company.corpCls());
        profile.setHmUrl(company.hmUrl());
        profile.setIrUrl(company.irUrl());
        profile.setIndutyCode(company.indutyCode());
        profile.setSourcePayloadVersion(firstNonBlank(company.sourcePayloadVersion(), corpCode.sourcePayloadVersion()));
        profile.setLastSyncedAt(LocalDateTime.now());
        stockIssuerProfileRepository.save(profile);
        return 1;
    }

    private boolean isIssuerProfileTarget(StockSymbol stockSymbol) {
        return stockSymbol.getMarket() == MarketType.KRX
                && stockSymbol.getAssetType() == AssetType.STOCK
                && stockSymbol.isActive();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }
}
