package com.folo.stock;

import com.folo.common.enums.MarketType;
import com.folo.config.MarketDataSyncProperties;
import com.folo.portfolio.HoldingRepository;
import com.folo.trade.TradeRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class StockEnrichmentTargetSelector {

    private final MarketDataSyncProperties properties;
    private final StockSymbolRepository stockSymbolRepository;
    private final HoldingRepository holdingRepository;
    private final TradeRepository tradeRepository;

    public StockEnrichmentTargetSelector(
            MarketDataSyncProperties properties,
            StockSymbolRepository stockSymbolRepository,
            HoldingRepository holdingRepository,
            TradeRepository tradeRepository
    ) {
        this.properties = properties;
        this.stockSymbolRepository = stockSymbolRepository;
        this.holdingRepository = holdingRepository;
        this.tradeRepository = tradeRepository;
    }

    public List<StockSymbol> resolvePrioritySymbols(List<MarketType> supportedMarkets) {
        if (supportedMarkets.isEmpty()) {
            return List.of();
        }

        int limit = Math.max(1, properties.batchSize());
        LinkedHashSet<Long> symbolIds = new LinkedHashSet<>();

        holdingRepository.findTopSymbolIdsByMarkets(supportedMarkets).stream()
                .limit(limit)
                .forEach(symbolIds::add);

        if (symbolIds.size() < limit) {
            tradeRepository.findTopSymbolIdsByMarkets(supportedMarkets).stream()
                    .filter(symbolId -> !symbolIds.contains(symbolId))
                    .limit(limit - symbolIds.size())
                    .forEach(symbolIds::add);
        }

        if (symbolIds.size() < limit) {
            stockSymbolRepository.findActiveByMarkets(supportedMarkets, PageRequest.of(0, limit * 2)).stream()
                    .map(StockSymbol::getId)
                    .filter(symbolId -> !symbolIds.contains(symbolId))
                    .limit(limit - symbolIds.size())
                    .forEach(symbolIds::add);
        }

        if (symbolIds.isEmpty()) {
            return List.of();
        }

        Map<Long, StockSymbol> symbolsById = stockSymbolRepository.findAllById(symbolIds).stream()
                .collect(Collectors.toMap(StockSymbol::getId, symbol -> symbol));

        List<StockSymbol> resolved = new ArrayList<>();
        for (Long symbolId : symbolIds) {
            StockSymbol stockSymbol = symbolsById.get(symbolId);
            if (stockSymbol != null) {
                resolved.add(stockSymbol);
            }
        }
        return resolved;
    }
}
