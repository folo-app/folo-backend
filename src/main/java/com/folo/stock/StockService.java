package com.folo.stock;

import com.folo.common.enums.MarketType;
import com.folo.common.exception.ApiException;
import com.folo.common.exception.ErrorCode;
import com.folo.portfolio.HoldingRepository;
import com.folo.trade.TradeRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class StockService {

    private final StockSymbolRepository stockSymbolRepository;
    private final PriceSnapshotRepository priceSnapshotRepository;
    private final HoldingRepository holdingRepository;
    private final TradeRepository tradeRepository;
    private final StockBrandingService stockBrandingService;
    private final KisQuoteService kisQuoteService;
    private final StockSymbolEnrichmentRepository stockSymbolEnrichmentRepository;

    public StockService(
            StockSymbolRepository stockSymbolRepository,
            PriceSnapshotRepository priceSnapshotRepository,
            HoldingRepository holdingRepository,
            TradeRepository tradeRepository,
            StockBrandingService stockBrandingService,
            KisQuoteService kisQuoteService,
            StockSymbolEnrichmentRepository stockSymbolEnrichmentRepository
    ) {
        this.stockSymbolRepository = stockSymbolRepository;
        this.priceSnapshotRepository = priceSnapshotRepository;
        this.holdingRepository = holdingRepository;
        this.tradeRepository = tradeRepository;
        this.stockBrandingService = stockBrandingService;
        this.kisQuoteService = kisQuoteService;
        this.stockSymbolEnrichmentRepository = stockSymbolEnrichmentRepository;
    }

    @Transactional(readOnly = true)
    public StockSearchResponse search(@Nullable String q, @Nullable String market) {
        if (q == null || q.trim().length() < 2) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "검색어는 2자 이상이어야 합니다.");
        }

        String normalizedQuery = q.trim();
        List<StockSymbol> stocks = stockSymbolRepository.searchTopByMarkets(
                resolveSearchMarkets(market),
                normalizedQuery,
                PageRequest.of(0, 20)
        );

        Map<Long, PriceSnapshot> snapshotBySymbolId = loadSnapshots(stocks);
        Map<Long, ResolvedStockQuote> liveQuotes = loadLiveQuotes(stocks);

        return new StockSearchResponse(stocks.stream()
                .map(stock -> toSearchItem(stock, liveQuotes.get(stock.getId()), snapshotBySymbolId.get(stock.getId())))
                .toList());
    }

    @Transactional(readOnly = true)
    public StockDiscoverResponse discover(int limit) {
        int normalizedLimit = Math.max(1, Math.min(limit, 24));

        return new StockDiscoverResponse(
                discoverForMarkets(List.of(MarketType.KRX), normalizedLimit),
                discoverForMarkets(List.of(MarketType.NASDAQ, MarketType.NYSE, MarketType.AMEX), normalizedLimit)
        );
    }

    @Transactional(readOnly = true)
    public StockPriceResponse getPrice(String ticker, String market) {
        StockSymbol stock = stockSymbolRepository.findByMarketAndTicker(MarketType.valueOf(market.toUpperCase()), ticker)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "종목을 찾을 수 없습니다."));

        ResolvedStockQuote liveQuote = loadLiveQuotes(List.of(stock)).get(stock.getId());
        if (liveQuote != null) {
            return toPriceResponse(stock, liveQuote);
        }

        PriceSnapshot snapshot = priceSnapshotRepository.findByStockSymbolId(stock.getId()).orElse(null);
        if (snapshot == null) {
            throw new ApiException(ErrorCode.NOT_FOUND, "시세 정보를 찾을 수 없습니다.");
        }

        return toPriceResponse(stock, snapshot);
    }

    @Transactional(readOnly = true)
    public StockSymbol getRequiredSymbol(MarketType market, String ticker) {
        return stockSymbolRepository.findByMarketAndTicker(market, ticker)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "종목을 찾을 수 없습니다."));
    }

    private List<StockSearchItem> discoverForMarkets(List<MarketType> markets, int limit) {
        LinkedHashSet<Long> symbolIds = new LinkedHashSet<>();

        holdingRepository.findTopSymbolIdsByMarkets(markets).stream()
                .limit(limit)
                .forEach(symbolIds::add);

        if (symbolIds.size() < limit) {
            tradeRepository.findTopSymbolIdsByMarkets(markets).stream()
                    .filter(symbolId -> !symbolIds.contains(symbolId))
                    .limit(limit - symbolIds.size())
                    .forEach(symbolIds::add);
        }

        if (symbolIds.size() < limit) {
            stockSymbolRepository.findActiveByMarkets(markets, PageRequest.of(0, limit * 2)).stream()
                    .map(StockSymbol::getId)
                    .filter(symbolId -> !symbolIds.contains(symbolId))
                    .limit(limit - symbolIds.size())
                    .forEach(symbolIds::add);
        }

        if (symbolIds.isEmpty()) {
            return List.of();
        }

        List<StockSymbol> symbols = stockSymbolRepository.findAllById(symbolIds);
        Map<Long, StockSymbol> symbolById = symbols.stream()
                .collect(Collectors.toMap(StockSymbol::getId, Function.identity()));

        Map<Long, PriceSnapshot> snapshotBySymbolId = loadSnapshots(symbols);
        Map<Long, ResolvedStockQuote> liveQuotes = loadLiveQuotes(symbols);

        List<StockSearchItem> result = new ArrayList<>();
        for (Long symbolId : symbolIds) {
            StockSymbol stock = symbolById.get(symbolId);
            if (stock == null) {
                continue;
            }

            result.add(toSearchItem(stock, liveQuotes.get(symbolId), snapshotBySymbolId.get(symbolId)));
        }

        return result;
    }

    private List<MarketType> resolveSearchMarkets(@Nullable String market) {
        if (market == null || market.isBlank() || market.equalsIgnoreCase("ALL")) {
            return List.of(MarketType.values());
        }

        if (market.equalsIgnoreCase("US")) {
            return List.of(MarketType.NASDAQ, MarketType.NYSE, MarketType.AMEX);
        }

        return List.of(MarketType.valueOf(market.toUpperCase()));
    }

    private Map<Long, PriceSnapshot> loadSnapshots(List<StockSymbol> symbols) {
        if (symbols.isEmpty()) {
            return Map.of();
        }

        return priceSnapshotRepository.findAllByStockSymbolIdIn(
                        symbols.stream().map(StockSymbol::getId).toList()
                ).stream()
                .collect(Collectors.toMap(snapshot -> snapshot.getStockSymbol().getId(), Function.identity()));
    }

    private Map<Long, ResolvedStockQuote> loadLiveQuotes(List<StockSymbol> symbols) {
        if (symbols.isEmpty()) {
            return Map.of();
        }

        Map<Long, ResolvedStockQuote> liveQuotes = kisQuoteService.fetchQuotes(symbols);
        if (!liveQuotes.isEmpty()) {
            upsertSnapshots(liveQuotes.values());
        }
        return liveQuotes;
    }

    private void upsertSnapshots(Iterable<ResolvedStockQuote> quotes) {
        for (ResolvedStockQuote quote : quotes) {
            PriceSnapshot snapshot = priceSnapshotRepository.findByStockSymbolId(quote.stockSymbol().getId())
                    .orElseGet(PriceSnapshot::new);
            snapshot.setStockSymbol(quote.stockSymbol());
            snapshot.setCurrentPrice(quote.currentPrice());
            snapshot.setOpenPrice(quote.openPrice());
            snapshot.setHighPrice(quote.highPrice());
            snapshot.setLowPrice(quote.lowPrice());
            snapshot.setDayReturn(quote.dayReturn());
            snapshot.setDayReturnRate(quote.dayReturnRate());
            snapshot.setMarketUpdatedAt(quote.marketUpdatedAt());
            priceSnapshotRepository.save(snapshot);
        }
    }

    private StockSearchItem toSearchItem(
            StockSymbol stock,
            ResolvedStockQuote liveQuote,
            PriceSnapshot snapshot
    ) {
        BigDecimal currentPrice = liveQuote != null
                ? liveQuote.currentPrice()
                : snapshot != null ? snapshot.getCurrentPrice() : BigDecimal.ZERO;
        BigDecimal dayReturnRate = liveQuote != null
                ? liveQuote.dayReturnRate()
                : snapshot != null ? snapshot.getDayReturnRate() : BigDecimal.ZERO;

        return new StockSearchItem(
                stock.getTicker(),
                stock.getName(),
                stock.getMarket(),
                stockBrandingService.getPublicLogoUrl(stock),
                currentPrice,
                dayReturnRate,
                stock.getSectorName()
        );
    }

    private StockPriceResponse toPriceResponse(StockSymbol stock, ResolvedStockQuote quote) {
        StockSymbolEnrichment enrichment = loadLatestEnrichment(stock.getId());
        return new StockPriceResponse(
                stock.getTicker(),
                stock.getName(),
                stock.getMarket(),
                quote.currentPrice(),
                quote.openPrice(),
                quote.highPrice(),
                quote.lowPrice(),
                quote.dayReturn(),
                quote.dayReturnRate(),
                stock.getSectorName(),
                enrichment != null ? enrichment.getIndustryNameRaw() : null,
                enrichment != null ? enrichment.getClassificationScheme().name() : null,
                quote.marketUpdatedAt().toString()
        );
    }

    private StockPriceResponse toPriceResponse(StockSymbol stock, PriceSnapshot snapshot) {
        StockSymbolEnrichment enrichment = loadLatestEnrichment(stock.getId());
        return new StockPriceResponse(
                stock.getTicker(),
                stock.getName(),
                stock.getMarket(),
                snapshot.getCurrentPrice(),
                snapshot.getOpenPrice(),
                snapshot.getHighPrice(),
                snapshot.getLowPrice(),
                snapshot.getDayReturn(),
                snapshot.getDayReturnRate(),
                stock.getSectorName(),
                enrichment != null ? enrichment.getIndustryNameRaw() : null,
                enrichment != null ? enrichment.getClassificationScheme().name() : null,
                snapshot.getMarketUpdatedAt().toString()
        );
    }

    private StockSymbolEnrichment loadLatestEnrichment(Long stockSymbolId) {
        return stockSymbolEnrichmentRepository.findTopByStockSymbolIdOrderByLastEnrichedAtDesc(stockSymbolId)
                .orElse(null);
    }
}
