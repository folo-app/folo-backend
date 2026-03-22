package com.folo.stock;

import com.folo.config.MarketDataSyncProperties;
import com.folo.common.enums.MarketType;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Service
public class StockMasterSyncService {

    private final MarketDataSyncProperties properties;
    private final List<StockMasterSyncProvider> providers;
    private final StockSymbolRepository stockSymbolRepository;
    private final StockSymbolSyncRunRepository stockSymbolSyncRunRepository;

    public StockMasterSyncService(
            MarketDataSyncProperties properties,
            List<StockMasterSyncProvider> providers,
            StockSymbolRepository stockSymbolRepository,
            StockSymbolSyncRunRepository stockSymbolSyncRunRepository
    ) {
        this.properties = properties;
        this.providers = providers;
        this.stockSymbolRepository = stockSymbolRepository;
        this.stockSymbolSyncRunRepository = stockSymbolSyncRunRepository;
    }

    public void syncAll() {
        for (StockMasterSyncProvider provider : providers) {
            for (MarketType market : Arrays.stream(MarketType.values()).filter(provider::supports).toList()) {
                syncMarket(provider, market);
            }
        }
    }

    @Transactional
    public void syncMarket(StockMasterSyncProvider provider, MarketType market) {
        LocalDateTime syncStartedAt = LocalDateTime.now();
        StockSymbolSyncRun syncRun = new StockSymbolSyncRun();
        syncRun.setProvider(provider.provider());
        syncRun.setMarket(market);
        syncRun.setSyncScope(StockSymbolSyncScope.MASTER);
        syncRun.setStartedAt(syncStartedAt);
        syncRun.setStatus(StockSymbolSyncStatus.STARTED);
        stockSymbolSyncRunRepository.save(syncRun);

        if (!provider.isConfigured()) {
            syncRun.setStatus(StockSymbolSyncStatus.SKIPPED);
            syncRun.setCompletedAt(LocalDateTime.now());
            syncRun.setErrorMessage("provider is not configured");
            stockSymbolSyncRunRepository.save(syncRun);
            return;
        }

        String cursor = null;
        int fetchedCount = 0;
        int upsertedCount = 0;
        int deactivatedCount = 0;

        try {
            do {
                syncRun.setRequestCursor(cursor);
                StockMasterSyncBatch batch = provider.fetchBatch(market, cursor, properties.batchSize());
                fetchedCount += batch.records().size();
                upsertedCount += upsertSymbols(provider.provider(), market, batch.records());
                cursor = batch.nextCursor();
                syncRun.setNextCursor(cursor);
            } while (cursor != null && !cursor.isBlank());

            deactivatedCount = deactivateMissingSymbols(provider.provider(), market, syncStartedAt);
            syncRun.setFetchedCount(fetchedCount);
            syncRun.setUpsertedCount(upsertedCount);
            syncRun.setDeactivatedCount(deactivatedCount);
            syncRun.setStatus(StockSymbolSyncStatus.COMPLETED);
            syncRun.setCompletedAt(LocalDateTime.now());
            stockSymbolSyncRunRepository.save(syncRun);
        } catch (RuntimeException exception) {
            syncRun.setFetchedCount(fetchedCount);
            syncRun.setUpsertedCount(upsertedCount);
            syncRun.setDeactivatedCount(deactivatedCount);
            syncRun.setStatus(StockSymbolSyncStatus.FAILED);
            syncRun.setCompletedAt(LocalDateTime.now());
            syncRun.setErrorMessage(exception.getMessage());
            stockSymbolSyncRunRepository.save(syncRun);
            throw exception;
        }
    }

    private int upsertSymbols(
            StockDataProvider provider,
            MarketType market,
            List<StockMasterSymbolRecord> records
    ) {
        int changed = 0;
        LocalDateTime syncedAt = LocalDateTime.now();

        for (StockMasterSymbolRecord record : records) {
            StockSymbol stockSymbol = stockSymbolRepository.findByMarketAndTicker(record.market(), record.ticker())
                    .orElseGet(StockSymbol::new);

            stockSymbol.setMarket(record.market());
            stockSymbol.setTicker(record.ticker());
            stockSymbol.setName(record.name());
            stockSymbol.setAssetType(record.assetType());
            stockSymbol.setActive(record.active());
            stockSymbol.setPrimaryExchangeCode(record.primaryExchangeCode());
            stockSymbol.setCurrencyCode(record.currencyCode());
            applyMasterMetadataFallback(stockSymbol, record);
            stockSymbol.setSourceProvider(provider);
            stockSymbol.setSourceIdentifier(record.sourceIdentifier());
            stockSymbol.setLastMasterSyncedAt(syncedAt);
            stockSymbolRepository.save(stockSymbol);
            changed++;
        }

        return changed;
    }

    private void applyMasterMetadataFallback(StockSymbol stockSymbol, StockMasterSymbolRecord record) {
        if (!StringUtils.hasText(stockSymbol.getSectorName())
                && StringUtils.hasText(record.sectorName())) {
            stockSymbol.setSectorName(record.sectorName().trim());
        }

        if (stockSymbol.getAnnualDividendYield() == null
                && hasPositiveValue(record.annualDividendYield())) {
            stockSymbol.setAnnualDividendYield(record.annualDividendYield());
        }

        if (!StringUtils.hasText(stockSymbol.getDividendMonthsCsv())
                && StringUtils.hasText(record.dividendMonthsCsv())) {
            stockSymbol.setDividendMonthsCsv(record.dividendMonthsCsv().trim());
        }
    }

    private boolean hasPositiveValue(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0;
    }

    private int deactivateMissingSymbols(
            StockDataProvider provider,
            MarketType market,
            LocalDateTime syncStartedAt
    ) {
        List<StockSymbol> symbols = stockSymbolRepository.findAllBySourceProviderAndMarket(provider, market);
        int deactivated = 0;

        for (StockSymbol symbol : symbols) {
            if (symbol.getLastMasterSyncedAt() != null
                    && symbol.getLastMasterSyncedAt().isBefore(syncStartedAt)
                    && symbol.isActive()) {
                symbol.setActive(false);
                stockSymbolRepository.save(symbol);
                deactivated++;
            }
        }

        return deactivated;
    }
}
