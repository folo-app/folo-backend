package com.folo.portfolio;

import com.folo.common.enums.PortfolioVisibility;
import com.folo.common.enums.ReturnVisibility;
import com.folo.common.exception.ApiException;
import com.folo.common.exception.ErrorCode;
import com.folo.follow.SocialRelationService;
import com.folo.stock.PriceSnapshot;
import com.folo.stock.PriceSnapshotRepository;
import com.folo.stock.StockSymbol;
import com.folo.user.User;
import com.folo.user.UserRepository;
import org.springframework.util.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.IntStream;

@Service
public class PortfolioService {

    private final PortfolioRepository portfolioRepository;
    private final HoldingRepository holdingRepository;
    private final PriceSnapshotRepository priceSnapshotRepository;
    private final UserRepository userRepository;
    private final SocialRelationService socialRelationService;

    public PortfolioService(
            PortfolioRepository portfolioRepository,
            HoldingRepository holdingRepository,
            PriceSnapshotRepository priceSnapshotRepository,
            UserRepository userRepository,
            SocialRelationService socialRelationService
    ) {
        this.portfolioRepository = portfolioRepository;
        this.holdingRepository = holdingRepository;
        this.priceSnapshotRepository = priceSnapshotRepository;
        this.userRepository = userRepository;
        this.socialRelationService = socialRelationService;
    }

    @Transactional(readOnly = true)
    public PortfolioResponse myPortfolio(Long userId) {
        User user = userRepository.findByIdAndActiveTrue(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "사용자를 찾을 수 없습니다."));
        return buildPortfolioResponse(user, true);
    }

    @Transactional(readOnly = true)
    public PortfolioResponse userPortfolio(Long currentUserId, Long targetUserId) {
        User target = userRepository.findByIdAndActiveTrue(targetUserId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "사용자를 찾을 수 없습니다."));

        boolean accessible = switch (target.getPortfolioVisibility()) {
            case PUBLIC -> true;
            case FRIENDS_ONLY -> socialRelationService.isMutualFollow(currentUserId, targetUserId);
            case PRIVATE -> currentUserId.equals(targetUserId);
        };

        if (!accessible) {
            throw new ApiException(ErrorCode.FORBIDDEN, "포트폴리오를 조회할 수 없습니다.");
        }

        return buildPortfolioResponse(target, currentUserId.equals(targetUserId));
    }

    private PortfolioResponse buildPortfolioResponse(User user, boolean ownerView) {
        Portfolio portfolio = portfolioRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "포트폴리오를 찾을 수 없습니다."));
        List<Holding> holdings = holdingRepository.findByUserIdOrderByIdAsc(user.getId());

        boolean fullyVisible = ownerView || user.getReturnVisibility() == ReturnVisibility.RATE_AND_AMOUNT;
        boolean rateOnly = !ownerView && user.getReturnVisibility() == ReturnVisibility.RATE_ONLY;

        List<PortfolioHoldingItem> items = holdings.stream()
                .map(holding -> {
                    StockSymbol stockSymbol = holding.getStockSymbol();
                    PriceSnapshot snapshot = priceSnapshotRepository.findByStockSymbolId(stockSymbol.getId()).orElse(null);
                    BigDecimal currentPrice = snapshot != null ? snapshot.getCurrentPrice() : holding.getAvgPrice();
                    return new PortfolioHoldingItem(
                            holding.getId(),
                            stockSymbol.getTicker(),
                            stockSymbol.getName(),
                            stockSymbol.getMarket().name(),
                            rateOnly ? null : holding.getQuantity(),
                            rateOnly ? null : holding.getAvgPrice(),
                            currentPrice,
                            rateOnly ? null : holding.getTotalInvested(),
                            rateOnly ? null : holding.getTotalValue(),
                            rateOnly ? null : holding.getReturnAmount(),
                            holding.getReturnRate(),
                            holding.getWeight(),
                            resolveSectorLabel(stockSymbol),
                            stockSymbol.getAssetType().name(),
                            stockSymbol.getCurrencyCode(),
                            stockSymbol.getAnnualDividendYield(),
                            parseDividendMonths(stockSymbol.getDividendMonthsCsv())
                    );
                })
                .toList();

        BigDecimal cashValue = computeCashValue(portfolio.getTotalValue(), items, fullyVisible);
        BigDecimal cashWeight = computeCashWeight(portfolio.getTotalValue(), items, cashValue, fullyVisible);
        List<PortfolioAllocationItem> sectorAllocations = buildSectorAllocations(items, cashValue, cashWeight, fullyVisible);
        List<PortfolioMonthlyDividendItem> monthlyDividendForecasts = buildMonthlyDividendForecasts(items, fullyVisible);

        return new PortfolioResponse(
                portfolio.getId(),
                fullyVisible ? portfolio.getTotalInvested() : null,
                fullyVisible ? portfolio.getTotalValue() : null,
                fullyVisible ? portfolio.getTotalReturnAmount() : null,
                portfolio.getTotalReturnRate(),
                fullyVisible ? portfolio.getDayReturnAmount() : null,
                portfolio.getDayReturnRate(),
                items,
                sectorAllocations,
                monthlyDividendForecasts,
                fullyVisible ? cashValue : null,
                cashWeight,
                portfolio.getSyncedAt() != null ? portfolio.getSyncedAt().toString() : null,
                fullyVisible
        );
    }

    private String resolveSectorLabel(StockSymbol stockSymbol) {
        if (StringUtils.hasText(stockSymbol.getSectorName())) {
            return stockSymbol.getSectorName().trim();
        }

        return switch (stockSymbol.getAssetType()) {
            case ETF -> "ETF";
            case STOCK -> "미분류";
        };
    }

    private List<Integer> parseDividendMonths(String csv) {
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

    private BigDecimal computeCashValue(
            BigDecimal portfolioTotalValue,
            List<PortfolioHoldingItem> items,
            boolean fullyVisible
    ) {
        if (!fullyVisible || portfolioTotalValue == null) {
            return BigDecimal.ZERO;
        }

        BigDecimal holdingsValue = items.stream()
                .map(PortfolioHoldingItem::totalValue)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal remaining = portfolioTotalValue.subtract(holdingsValue);
        return remaining.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal computeCashWeight(
            BigDecimal portfolioTotalValue,
            List<PortfolioHoldingItem> items,
            BigDecimal cashValue,
            boolean fullyVisible
    ) {
        if (fullyVisible && portfolioTotalValue != null && portfolioTotalValue.compareTo(BigDecimal.ZERO) > 0) {
            return cashValue.multiply(BigDecimal.valueOf(100))
                    .divide(portfolioTotalValue, 4, RoundingMode.HALF_UP);
        }

        BigDecimal sumWeights = items.stream()
                .map(PortfolioHoldingItem::weight)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal remaining = BigDecimal.valueOf(100).subtract(sumWeights);
        return remaining.max(BigDecimal.ZERO).setScale(4, RoundingMode.HALF_UP);
    }

    private List<PortfolioAllocationItem> buildSectorAllocations(
            List<PortfolioHoldingItem> items,
            BigDecimal cashValue,
            BigDecimal cashWeight,
            boolean fullyVisible
    ) {
        Map<String, AllocationAccumulator> grouped = new LinkedHashMap<>();

        for (PortfolioHoldingItem item : items) {
            String label = StringUtils.hasText(item.sectorName()) ? item.sectorName() : "미분류";
            AllocationAccumulator accumulator = grouped.computeIfAbsent(label, ignored -> new AllocationAccumulator());
            accumulator.weight = accumulator.weight.add(item.weight());
            if (fullyVisible && item.totalValue() != null) {
                accumulator.value = accumulator.value.add(item.totalValue());
            }
        }

        if (cashWeight.compareTo(BigDecimal.ZERO) > 0 || cashValue.compareTo(BigDecimal.ZERO) > 0) {
            AllocationAccumulator cashAccumulator = grouped.computeIfAbsent("현금", ignored -> new AllocationAccumulator());
            cashAccumulator.weight = cashAccumulator.weight.add(cashWeight);
            if (fullyVisible) {
                cashAccumulator.value = cashAccumulator.value.add(cashValue);
            }
        }

        return grouped.entrySet().stream()
                .map(entry -> new PortfolioAllocationItem(
                        allocationKey(entry.getKey()),
                        entry.getKey(),
                        entry.getValue().weight.setScale(4, RoundingMode.HALF_UP),
                        fullyVisible ? entry.getValue().value.setScale(2, RoundingMode.HALF_UP) : null
                ))
                .sorted((left, right) -> right.weight().compareTo(left.weight()))
                .toList();
    }

    private List<PortfolioMonthlyDividendItem> buildMonthlyDividendForecasts(
            List<PortfolioHoldingItem> items,
            boolean fullyVisible
    ) {
        if (!fullyVisible) {
            return List.of();
        }

        Map<Integer, BigDecimal> monthAmounts = new LinkedHashMap<>();
        IntStream.rangeClosed(1, 12).forEach(month -> monthAmounts.put(month, BigDecimal.ZERO));

        for (PortfolioHoldingItem item : items) {
            if (item.totalValue() == null
                    || item.annualDividendYield() == null
                    || item.annualDividendYield().compareTo(BigDecimal.ZERO) <= 0
                    || item.dividendMonths().isEmpty()) {
                continue;
            }

            BigDecimal annualDividend = item.totalValue()
                    .multiply(item.annualDividendYield())
                    .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
            BigDecimal perMonthAmount = annualDividend
                    .divide(BigDecimal.valueOf(item.dividendMonths().size()), 4, RoundingMode.HALF_UP);

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

    private String allocationKey(String label) {
        return label.replaceAll("\\s+", "-").toLowerCase();
    }

    private static class AllocationAccumulator {
        private BigDecimal weight = BigDecimal.ZERO;
        private BigDecimal value = BigDecimal.ZERO;
    }
}
