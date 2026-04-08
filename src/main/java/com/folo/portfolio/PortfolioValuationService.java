package com.folo.portfolio;

import com.folo.common.enums.CurrencyCode;
import com.folo.common.exception.ApiException;
import com.folo.stock.PriceSnapshot;
import com.folo.stock.PriceSnapshotRepository;
import com.folo.stock.StockSectorNormalizer;
import com.folo.stock.StockSymbol;
import com.folo.stock.StockSymbolEnrichment;
import com.folo.stock.StockSymbolEnrichmentRepository;
import com.folo.fx.FxRateService;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
public class PortfolioValuationService {

    private static final int MONEY_SCALE = 4;
    private static final int WEIGHT_SCALE = 4;

    private final PriceSnapshotRepository priceSnapshotRepository;
    private final StockSymbolEnrichmentRepository stockSymbolEnrichmentRepository;
    private final FxRateService fxRateService;

    public PortfolioValuationService(
            PriceSnapshotRepository priceSnapshotRepository,
            StockSymbolEnrichmentRepository stockSymbolEnrichmentRepository,
            FxRateService fxRateService
    ) {
        this.priceSnapshotRepository = priceSnapshotRepository;
        this.stockSymbolEnrichmentRepository = stockSymbolEnrichmentRepository;
        this.fxRateService = fxRateService;
    }

    @Transactional(readOnly = true, noRollbackFor = ApiException.class)
    public PortfolioValuationResult valuate(List<Holding> holdings, CurrencyCode displayCurrency) {
        if (holdings.isEmpty()) {
            return new PortfolioValuationResult(
                    BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP),
                    BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP),
                    BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP),
                    BigDecimal.ZERO.setScale(WEIGHT_SCALE, RoundingMode.HALF_UP),
                    BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP),
                    BigDecimal.ZERO.setScale(WEIGHT_SCALE, RoundingMode.HALF_UP),
                    List.of(),
                    List.of(),
                    emptyMonthlyDividendForecasts(),
                    BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP),
                    BigDecimal.ZERO.setScale(WEIGHT_SCALE, RoundingMode.HALF_UP),
                    displayCurrency,
                    null,
                    false
            );
        }

        List<Long> stockSymbolIds = holdings.stream()
                .map(Holding::getStockSymbol)
                .filter(Objects::nonNull)
                .map(StockSymbol::getId)
                .toList();
        Map<Long, PriceSnapshot> snapshotBySymbolId = loadSnapshots(stockSymbolIds);
        Map<Long, StockSymbolEnrichment> enrichmentBySymbolId = loadLatestEnrichments(stockSymbolIds);

        List<HoldingValuation> valuations = new ArrayList<>();
        BigDecimal totalInvested = BigDecimal.ZERO;
        BigDecimal totalValue = BigDecimal.ZERO;
        BigDecimal dayReturn = BigDecimal.ZERO;
        LocalDateTime fxAsOf = null;
        boolean fxStale = false;

        for (Holding holding : holdings) {
            StockSymbol stockSymbol = holding.getStockSymbol();
            PriceSnapshot snapshot = snapshotBySymbolId.get(stockSymbol.getId());
            StockSymbolEnrichment enrichment = enrichmentBySymbolId.get(stockSymbol.getId());

            CurrencyCode nativeCurrency = CurrencyCode.fromMarketAndRaw(
                    stockSymbol.getMarket(),
                    stockSymbol.getCurrencyCode()
            );
            BigDecimal currentPrice = snapshot != null && snapshot.getCurrentPrice() != null
                    ? snapshot.getCurrentPrice()
                    : holding.getAvgPrice();
            BigDecimal nativeTotalInvested = normalizeMoney(holding.getTotalInvested());
            BigDecimal nativeTotalValue = holding.getQuantity()
                    .multiply(currentPrice)
                    .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            BigDecimal nativeReturnAmount = nativeTotalValue.subtract(nativeTotalInvested)
                    .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            BigDecimal nativeDayReturn = snapshot != null && snapshot.getDayReturn() != null
                    ? holding.getQuantity().multiply(snapshot.getDayReturn()).setScale(MONEY_SCALE, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);

            FxRateService.ConversionResult displayTotalInvested = fxRateService.convert(
                    nativeTotalInvested,
                    nativeCurrency,
                    displayCurrency
            );
            FxRateService.ConversionResult displayTotalValue = fxRateService.convert(
                    nativeTotalValue,
                    nativeCurrency,
                    displayCurrency
            );
            FxRateService.ConversionResult displayReturnAmount = fxRateService.convert(
                    nativeReturnAmount,
                    nativeCurrency,
                    displayCurrency
            );
            FxRateService.ConversionResult displayDayReturn = fxRateService.convert(
                    nativeDayReturn,
                    nativeCurrency,
                    displayCurrency
            );

            fxAsOf = mergeFxAsOf(fxAsOf, displayTotalInvested.asOf());
            fxAsOf = mergeFxAsOf(fxAsOf, displayTotalValue.asOf());
            fxAsOf = mergeFxAsOf(fxAsOf, displayReturnAmount.asOf());
            fxAsOf = mergeFxAsOf(fxAsOf, displayDayReturn.asOf());
            fxStale = fxStale
                    || displayTotalInvested.stale()
                    || displayTotalValue.stale()
                    || displayReturnAmount.stale()
                    || displayDayReturn.stale();

            totalInvested = totalInvested.add(displayTotalInvested.amount());
            totalValue = totalValue.add(displayTotalValue.amount());
            dayReturn = dayReturn.add(displayDayReturn.amount());

            StockSectorNormalizer.ResolvedSector resolvedSector = StockSectorNormalizer.resolve(
                    stockSymbol.getAssetType(),
                    stockSymbol.getSectorCode(),
                    stockSymbol.getSectorName(),
                    enrichment != null ? enrichment.getSectorNameRaw() : null,
                    enrichment != null ? enrichment.getIndustryNameRaw() : null,
                    enrichment != null ? enrichment.getClassificationScheme() : null
            );

            valuations.add(new HoldingValuation(
                    holding,
                    currentPrice,
                    nativeTotalInvested,
                    nativeTotalValue,
                    nativeReturnAmount,
                    percent(nativeReturnAmount, nativeTotalInvested),
                    resolvedSector,
                    parseDividendMonths(stockSymbol.getDividendMonthsCsv()),
                    displayTotalInvested.amount(),
                    displayTotalValue.amount(),
                    displayReturnAmount.amount()
            ));
        }

        totalInvested = normalizeMoney(totalInvested);
        totalValue = normalizeMoney(totalValue);
        BigDecimal totalReturn = totalValue.subtract(totalInvested).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        dayReturn = normalizeMoney(dayReturn);
        BigDecimal previousValue = totalValue.subtract(dayReturn);
        BigDecimal totalReturnRate = percent(totalReturn, totalInvested);
        BigDecimal dayReturnRate = percent(dayReturn, previousValue);

        BigDecimal portfolioTotalValue = totalValue;
        List<PortfolioHoldingItem> items = valuations.stream()
                .map(valuation -> toHoldingItem(valuation, portfolioTotalValue))
                .toList();

        return new PortfolioValuationResult(
                totalInvested,
                totalValue,
                totalReturn,
                totalReturnRate,
                dayReturn,
                dayReturnRate,
                items,
                buildSectorAllocations(items),
                buildMonthlyDividendForecasts(items),
                BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP),
                BigDecimal.ZERO.setScale(WEIGHT_SCALE, RoundingMode.HALF_UP),
                displayCurrency,
                fxAsOf,
                fxStale
        );
    }

    private Map<Long, PriceSnapshot> loadSnapshots(List<Long> stockSymbolIds) {
        return priceSnapshotRepository.findAllByStockSymbolIdIn(stockSymbolIds).stream()
                .collect(Collectors.toMap(snapshot -> snapshot.getStockSymbol().getId(), Function.identity()));
    }

    private Map<Long, StockSymbolEnrichment> loadLatestEnrichments(List<Long> stockSymbolIds) {
        Map<Long, StockSymbolEnrichment> latestBySymbolId = new LinkedHashMap<>();
        for (StockSymbolEnrichment enrichment : stockSymbolEnrichmentRepository.findLatestCandidatesByStockSymbolIds(stockSymbolIds)) {
            latestBySymbolId.putIfAbsent(enrichment.getStockSymbol().getId(), enrichment);
        }
        return latestBySymbolId;
    }

    private PortfolioHoldingItem toHoldingItem(HoldingValuation valuation, BigDecimal portfolioTotalValue) {
        Holding holding = valuation.holding();
        StockSymbol stockSymbol = holding.getStockSymbol();
        BigDecimal weight = portfolioTotalValue.compareTo(BigDecimal.ZERO) > 0
                ? valuation.displayTotalValue()
                .multiply(BigDecimal.valueOf(100))
                .divide(portfolioTotalValue, WEIGHT_SCALE, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(WEIGHT_SCALE, RoundingMode.HALF_UP);

        return new PortfolioHoldingItem(
                holding.getId(),
                stockSymbol.getTicker(),
                stockSymbol.getName(),
                stockSymbol.getMarket().name(),
                holding.getQuantity(),
                holding.getAvgPrice(),
                valuation.currentPrice(),
                valuation.nativeTotalInvested(),
                valuation.nativeTotalValue(),
                valuation.nativeReturnAmount(),
                valuation.returnRate(),
                weight,
                valuation.sector().key(),
                valuation.sector().label(),
                stockSymbol.getAssetType().name(),
                stockSymbol.getCurrencyCode(),
                stockSymbol.getAnnualDividendYield(),
                valuation.dividendMonths(),
                valuation.displayTotalInvested(),
                valuation.displayTotalValue(),
                valuation.displayReturnAmount()
        );
    }

    private List<PortfolioAllocationItem> buildSectorAllocations(List<PortfolioHoldingItem> items) {
        Map<String, AllocationAccumulator> grouped = new LinkedHashMap<>();

        for (PortfolioHoldingItem item : items) {
            String key = StringUtils.hasText(item.sectorCode()) ? item.sectorCode() : "other";
            String label = StringUtils.hasText(item.sectorName()) ? item.sectorName() : "기타";
            AllocationAccumulator accumulator = grouped.computeIfAbsent(
                    key,
                    ignored -> new AllocationAccumulator(label)
            );
            accumulator.weight = accumulator.weight.add(item.weight());
            if (item.displayTotalValue() != null) {
                accumulator.value = accumulator.value.add(item.displayTotalValue());
            }
        }

        return grouped.entrySet().stream()
                .map(entry -> new PortfolioAllocationItem(
                        entry.getKey(),
                        entry.getValue().label,
                        entry.getValue().weight.setScale(WEIGHT_SCALE, RoundingMode.HALF_UP),
                        entry.getValue().value.setScale(MONEY_SCALE, RoundingMode.HALF_UP)
                ))
                .sorted((left, right) -> right.weight().compareTo(left.weight()))
                .toList();
    }

    private List<PortfolioMonthlyDividendItem> buildMonthlyDividendForecasts(List<PortfolioHoldingItem> items) {
        Map<Integer, BigDecimal> monthAmounts = new LinkedHashMap<>();
        IntStream.rangeClosed(1, 12).forEach(month -> monthAmounts.put(month, BigDecimal.ZERO));

        for (PortfolioHoldingItem item : items) {
            if (item.displayTotalValue() == null
                    || item.annualDividendYield() == null
                    || item.annualDividendYield().compareTo(BigDecimal.ZERO) <= 0
                    || item.dividendMonths().isEmpty()) {
                continue;
            }

            BigDecimal annualDividend = item.displayTotalValue()
                    .multiply(item.annualDividendYield())
                    .divide(BigDecimal.valueOf(100), MONEY_SCALE, RoundingMode.HALF_UP);
            BigDecimal perMonthAmount = annualDividend
                    .divide(BigDecimal.valueOf(item.dividendMonths().size()), MONEY_SCALE, RoundingMode.HALF_UP);

            for (Integer month : item.dividendMonths()) {
                monthAmounts.computeIfPresent(month, (ignored, amount) -> amount.add(perMonthAmount));
            }
        }

        return monthAmounts.entrySet().stream()
                .map(entry -> new PortfolioMonthlyDividendItem(
                        entry.getKey(),
                        entry.getKey() + "월",
                        entry.getValue().setScale(2, RoundingMode.HALF_UP)
                ))
                .toList();
    }

    private List<PortfolioMonthlyDividendItem> emptyMonthlyDividendForecasts() {
        return IntStream.rangeClosed(1, 12)
                .mapToObj(month -> new PortfolioMonthlyDividendItem(
                        month,
                        month + "월",
                        BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                ))
                .toList();
    }

    private List<Integer> parseDividendMonths(@Nullable String csv) {
        if (!StringUtils.hasText(csv)) {
            return List.of();
        }

        return List.of(csv.split(",")).stream()
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(this::parseMonth)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
    }

    private Integer parseMonth(String raw) {
        try {
            int month = Integer.parseInt(raw);
            return month >= 1 && month <= 12 ? month : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private BigDecimal percent(BigDecimal amount, BigDecimal base) {
        if (base == null || base.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(WEIGHT_SCALE, RoundingMode.HALF_UP);
        }
        return amount.multiply(BigDecimal.valueOf(100))
                .divide(base, WEIGHT_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal normalizeMoney(BigDecimal value) {
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private @Nullable LocalDateTime mergeFxAsOf(
            @Nullable LocalDateTime current,
            @Nullable LocalDateTime candidate
    ) {
        if (candidate == null) {
            return current;
        }
        if (current == null || candidate.isBefore(current)) {
            return candidate;
        }
        return current;
    }

    public record PortfolioValuationResult(
            BigDecimal totalInvested,
            BigDecimal totalValue,
            BigDecimal totalReturn,
            BigDecimal totalReturnRate,
            BigDecimal dayReturn,
            BigDecimal dayReturnRate,
            List<PortfolioHoldingItem> holdings,
            List<PortfolioAllocationItem> sectorAllocations,
            List<PortfolioMonthlyDividendItem> monthlyDividendForecasts,
            BigDecimal cashValue,
            BigDecimal cashWeight,
            CurrencyCode displayCurrency,
            @Nullable LocalDateTime fxAsOf,
            boolean fxStale
    ) {
    }

    private record HoldingValuation(
            Holding holding,
            BigDecimal currentPrice,
            BigDecimal nativeTotalInvested,
            BigDecimal nativeTotalValue,
            BigDecimal nativeReturnAmount,
            BigDecimal returnRate,
            StockSectorNormalizer.ResolvedSector sector,
            List<Integer> dividendMonths,
            BigDecimal displayTotalInvested,
            BigDecimal displayTotalValue,
            BigDecimal displayReturnAmount
    ) {
    }

    private static final class AllocationAccumulator {
        private final String label;
        private BigDecimal weight = BigDecimal.ZERO;
        private BigDecimal value = BigDecimal.ZERO;

        private AllocationAccumulator(String label) {
            this.label = label;
        }
    }
}
