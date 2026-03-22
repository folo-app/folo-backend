package com.folo.stock;

import com.folo.common.enums.AssetType;
import com.folo.common.enums.MarketType;
import com.folo.portfolio.Holding;
import com.folo.portfolio.HoldingRepository;
import com.folo.security.SecurityUtils;
import com.folo.trade.TradeRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class StockRecommendationService {

    private static final int POPULARITY_POOL_SIZE = 80;
    private static final int FALLBACK_POOL_SIZE = 120;
    private static final List<String> BLOCKED_NAME_TOKENS = List.of(
            " ETN",
            "인버스",
            "레버리지",
            " BULL",
            " BEAR",
            " ULTRA",
            " 2X",
            " 3X",
            "-1X",
            "-2X",
            "-3X"
    );

    private static final Map<MarketType, List<String>> LARGE_CAP_SEEDS = Map.of(
            MarketType.KRX, List.of(
                    "005930", "000660", "035420", "005380", "207940", "068270",
                    "051910", "105560", "055550", "012450", "035720", "323410",
                    "086790", "066570", "003670", "096770", "028260", "034020"
            ),
            MarketType.NASDAQ, List.of(
                    "AAPL", "MSFT", "NVDA", "GOOGL", "AMZN", "META", "AVGO",
                    "TSLA", "AMD", "NFLX", "COST", "QQQ", "PLTR", "INTC"
            ),
            MarketType.NYSE, List.of(
                    "VOO", "SPY", "BRK.B", "JPM", "V", "MA", "LLY", "XOM",
                    "WMT", "JNJ", "PG", "KO", "HD", "UNH"
            ),
            MarketType.AMEX, List.of(
                    "GLD", "SLV", "GDX", "XBI", "ARKK", "USO"
            )
    );

    private final StockSymbolRepository stockSymbolRepository;
    private final HoldingRepository holdingRepository;
    private final TradeRepository tradeRepository;

    public StockRecommendationService(
            StockSymbolRepository stockSymbolRepository,
            HoldingRepository holdingRepository,
            TradeRepository tradeRepository
    ) {
        this.stockSymbolRepository = stockSymbolRepository;
        this.holdingRepository = holdingRepository;
        this.tradeRepository = tradeRepository;
    }

    public List<StockSymbol> recommendForMarkets(List<MarketType> markets, int limit) {
        int normalizedLimit = Math.max(1, Math.min(limit, 24));
        RecommendationContext context = buildContext(markets, SecurityUtils.currentUserIdOrNull());
        List<StockSymbol> candidates = loadCandidates(markets, context, normalizedLimit);
        if (candidates.isEmpty()) {
            return List.of();
        }

        Map<Long, StockPopularityStat> holdingStats = holdingRepository.findTopRecommendationStatsByMarkets(
                markets,
                PageRequest.of(0, POPULARITY_POOL_SIZE)
        ).stream().collect(Collectors.toMap(StockPopularityStat::stockSymbolId, stat -> stat));
        Map<Long, StockPopularityStat> tradeStats = tradeRepository.findTopRecommendationStatsByMarkets(
                markets,
                PageRequest.of(0, POPULARITY_POOL_SIZE)
        ).stream().collect(Collectors.toMap(StockPopularityStat::stockSymbolId, stat -> stat));
        Map<String, Integer> seedRank = buildSeedRank(markets);

        return candidates.stream()
                .filter(symbol -> isEligibleCandidate(symbol, context, holdingStats, tradeStats, seedRank))
                .sorted(Comparator
                        .comparingDouble((StockSymbol symbol) -> score(symbol, context, holdingStats, tradeStats, seedRank))
                        .reversed()
                        .thenComparing(StockSymbol::getName, String.CASE_INSENSITIVE_ORDER))
                .limit(normalizedLimit)
                .toList();
    }

    private RecommendationContext buildContext(List<MarketType> markets, @Nullable Long currentUserId) {
        if (currentUserId == null) {
            return RecommendationContext.empty();
        }

        List<Holding> holdings = holdingRepository.findByUserIdOrderByIdAsc(currentUserId).stream()
                .filter(holding -> holding.getStockSymbol() != null)
                .filter(holding -> markets.contains(holding.getStockSymbol().getMarket()))
                .toList();
        if (holdings.isEmpty()) {
            return RecommendationContext.empty();
        }

        Set<Long> excludedSymbolIds = new HashSet<>();
        Map<MarketType, Double> marketWeights = new EnumMap<>(MarketType.class);
        Map<String, Double> sectorWeights = new HashMap<>();
        double totalInvested = 0D;

        for (Holding holding : holdings) {
            StockSymbol symbol = holding.getStockSymbol();
            excludedSymbolIds.add(symbol.getId());
            double invested = positiveDouble(holding.getTotalInvested());
            totalInvested += invested;
            marketWeights.merge(symbol.getMarket(), invested, Double::sum);
            if (symbol.getSectorName() != null && !symbol.getSectorName().isBlank()) {
                sectorWeights.merge(symbol.getSectorName().trim().toUpperCase(Locale.ROOT), invested, Double::sum);
            }
        }

        if (totalInvested <= 0D) {
            totalInvested = holdings.size();
        }

        normalize(marketWeights, totalInvested);
        normalize(sectorWeights, totalInvested);
        return new RecommendationContext(excludedSymbolIds, marketWeights, sectorWeights);
    }

    private List<StockSymbol> loadCandidates(List<MarketType> markets, RecommendationContext context, int limit) {
        LinkedHashSet<Long> candidateIds = new LinkedHashSet<>();

        holdingRepository.findTopRecommendationStatsByMarkets(markets, PageRequest.of(0, POPULARITY_POOL_SIZE)).stream()
                .map(StockPopularityStat::stockSymbolId)
                .forEach(candidateIds::add);

        tradeRepository.findTopRecommendationStatsByMarkets(markets, PageRequest.of(0, POPULARITY_POOL_SIZE)).stream()
                .map(StockPopularityStat::stockSymbolId)
                .forEach(candidateIds::add);

        for (MarketType market : markets) {
            List<String> seedTickers = LARGE_CAP_SEEDS.getOrDefault(market, List.of());
            if (!seedTickers.isEmpty()) {
                stockSymbolRepository.findByMarketAndTickerInAndActiveTrue(market, seedTickers).stream()
                        .map(StockSymbol::getId)
                        .forEach(candidateIds::add);
            }
        }

        List<String> preferredSectors = context.sectorWeights().entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(3)
                .map(Map.Entry::getKey)
                .toList();
        if (!preferredSectors.isEmpty()) {
            stockSymbolRepository.findActiveByMarketsAndSectorNames(markets, preferredSectors, PageRequest.of(0, 40)).stream()
                    .map(StockSymbol::getId)
                    .forEach(candidateIds::add);
        }

        stockSymbolRepository.findActiveByMarkets(markets, PageRequest.of(0, Math.max(FALLBACK_POOL_SIZE, limit * 8))).stream()
                .map(StockSymbol::getId)
                .forEach(candidateIds::add);

        if (candidateIds.isEmpty()) {
            return List.of();
        }

        return stockSymbolRepository.findAllById(candidateIds);
    }

    private boolean isEligibleCandidate(
            StockSymbol symbol,
            RecommendationContext context,
            Map<Long, StockPopularityStat> holdingStats,
            Map<Long, StockPopularityStat> tradeStats,
            Map<String, Integer> seedRank
    ) {
        if (!symbol.isActive() || context.excludedSymbolIds().contains(symbol.getId())) {
            return false;
        }

        String normalizedName = symbol.getName() == null ? "" : symbol.getName().toUpperCase(Locale.ROOT);
        boolean blockedByName = BLOCKED_NAME_TOKENS.stream().anyMatch(normalizedName::contains);
        if (blockedByName) {
            return false;
        }

        boolean hasPopularitySignal = holdingStats.containsKey(symbol.getId()) || tradeStats.containsKey(symbol.getId());
        boolean seeded = seedRank.containsKey(seedKey(symbol));
        return symbol.getAssetType() != AssetType.ETF || seeded || hasPopularitySignal;
    }

    private double score(
            StockSymbol symbol,
            RecommendationContext context,
            Map<Long, StockPopularityStat> holdingStats,
            Map<Long, StockPopularityStat> tradeStats,
            Map<String, Integer> seedRank
    ) {
        double score = 0D;

        StockPopularityStat holdingStat = holdingStats.get(symbol.getId());
        if (holdingStat != null) {
            score += Math.min(holdingStat.popularityCount(), 50L) * 12D;
            score += Math.min(positiveDouble(holdingStat.popularityAmount()) / 1_000_000D, 300D);
        }

        StockPopularityStat tradeStat = tradeStats.get(symbol.getId());
        if (tradeStat != null) {
            score += Math.min(tradeStat.popularityCount(), 80L) * 8D;
            score += Math.min(positiveDouble(tradeStat.popularityAmount()) / 1_000_000D, 240D);
        }

        Integer rank = seedRank.get(seedKey(symbol));
        if (rank != null) {
            score += 500D - (rank * 18D);
        }

        if (symbol.getAssetType() == AssetType.STOCK) {
            score += 40D;
        } else {
            score += 10D;
        }

        score += context.marketWeights().getOrDefault(symbol.getMarket(), 0D) * 180D;
        if (symbol.getSectorName() != null) {
            score += context.sectorWeights().getOrDefault(
                    symbol.getSectorName().trim().toUpperCase(Locale.ROOT),
                    0D
            ) * 220D;
        }

        if (symbol.getAnnualDividendYield() != null) {
            score += Math.min(symbol.getAnnualDividendYield().doubleValue(), 8D) * 4D;
        }

        return score;
    }

    private Map<String, Integer> buildSeedRank(Collection<MarketType> markets) {
        Map<String, Integer> rank = new HashMap<>();
        for (MarketType market : markets) {
            List<String> seeds = LARGE_CAP_SEEDS.getOrDefault(market, List.of());
            for (int index = 0; index < seeds.size(); index++) {
                rank.put(market + ":" + seeds.get(index).toUpperCase(Locale.ROOT), index);
            }
        }
        return rank;
    }

    private void normalize(Map<?, Double> weights, double total) {
        if (total <= 0D) {
            return;
        }
        weights.replaceAll((key, value) -> value / total);
    }

    private double positiveDouble(@Nullable BigDecimal value) {
        if (value == null) {
            return 0D;
        }
        return Math.max(0D, value.doubleValue());
    }

    private String seedKey(StockSymbol symbol) {
        return symbol.getMarket() + ":" + symbol.getTicker().toUpperCase(Locale.ROOT);
    }

    private record RecommendationContext(
            Set<Long> excludedSymbolIds,
            Map<MarketType, Double> marketWeights,
            Map<String, Double> sectorWeights
    ) {
        static RecommendationContext empty() {
            return new RecommendationContext(Set.of(), Map.of(), Map.of());
        }
    }
}
