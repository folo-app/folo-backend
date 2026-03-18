package com.folo.portfolio;

import com.folo.common.enums.TradeType;
import com.folo.common.exception.ApiException;
import com.folo.common.exception.ErrorCode;
import com.folo.stock.PriceSnapshot;
import com.folo.stock.PriceSnapshotRepository;
import com.folo.trade.Trade;
import com.folo.trade.TradeRepository;
import com.folo.user.User;
import com.folo.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class PortfolioProjectionService {

    private final TradeRepository tradeRepository;
    private final HoldingRepository holdingRepository;
    private final PortfolioRepository portfolioRepository;
    private final PriceSnapshotRepository priceSnapshotRepository;
    private final UserRepository userRepository;

    public PortfolioProjectionService(
            TradeRepository tradeRepository,
            HoldingRepository holdingRepository,
            PortfolioRepository portfolioRepository,
            PriceSnapshotRepository priceSnapshotRepository,
            UserRepository userRepository
    ) {
        this.tradeRepository = tradeRepository;
        this.holdingRepository = holdingRepository;
        this.portfolioRepository = portfolioRepository;
        this.priceSnapshotRepository = priceSnapshotRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public void recalculate(Long userId) {
        User user = userRepository.findByIdAndActiveTrue(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "사용자를 찾을 수 없습니다."));
        List<Trade> trades = tradeRepository.findByUserIdAndDeletedFalseOrderByTradedAtAscIdAsc(userId);

        Map<Long, Aggregate> aggregates = new HashMap<>();
        for (Trade trade : trades) {
            Aggregate aggregate = aggregates.computeIfAbsent(trade.getStockSymbol().getId(), key -> new Aggregate(trade.getStockSymbol()));
            aggregate.apply(trade);
        }

        List<Holding> existingHoldings = holdingRepository.findByUserIdOrderByIdAsc(userId);
        Map<Long, Holding> existingBySymbolId = existingHoldings.stream()
                .collect(Collectors.toMap(holding -> holding.getStockSymbol().getId(), holding -> holding));

        Set<Long> activeSymbolIds = aggregates.entrySet().stream()
                .filter(entry -> entry.getValue().quantity.compareTo(BigDecimal.ZERO) > 0)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());

        existingHoldings.stream()
                .filter(holding -> !activeSymbolIds.contains(holding.getStockSymbol().getId()))
                .forEach(holdingRepository::delete);

        BigDecimal totalInvested = BigDecimal.ZERO;
        BigDecimal totalValue = BigDecimal.ZERO;
        BigDecimal dayReturnAmount = BigDecimal.ZERO;

        Map<Long, PriceSnapshot> priceSnapshots = new HashMap<>();
        for (Long symbolId : activeSymbolIds) {
            priceSnapshots.put(symbolId, priceSnapshotRepository.findByStockSymbolId(symbolId).orElse(null));
        }

        for (Long symbolId : activeSymbolIds) {
            Aggregate aggregate = aggregates.get(symbolId);
            PriceSnapshot priceSnapshot = priceSnapshots.get(symbolId);
            BigDecimal currentPrice = priceSnapshot != null ? priceSnapshot.getCurrentPrice() : aggregate.averagePrice();
            BigDecimal totalHoldingValue = aggregate.quantity.multiply(currentPrice).setScale(4, RoundingMode.HALF_UP);
            BigDecimal returnAmount = totalHoldingValue.subtract(aggregate.totalInvested).setScale(4, RoundingMode.HALF_UP);
            BigDecimal returnRate = percent(returnAmount, aggregate.totalInvested);

            Holding holding = existingBySymbolId.getOrDefault(symbolId, new Holding());
            holding.setUser(user);
            holding.setStockSymbol(aggregate.stockSymbol);
            holding.setQuantity(aggregate.quantity);
            holding.setAvgPrice(aggregate.averagePrice());
            holding.setTotalInvested(aggregate.totalInvested.setScale(4, RoundingMode.HALF_UP));
            holding.setTotalValue(totalHoldingValue);
            holding.setReturnAmount(returnAmount);
            holding.setReturnRate(returnRate);
            holding.setWeight(BigDecimal.ZERO);
            holding.setFirstBoughtDate(aggregate.firstBoughtDate);
            holding.setLastTradeDate(aggregate.lastTradeDate);
            holding.setCalculatedAt(LocalDateTime.now());
            holdingRepository.save(holding);

            totalInvested = totalInvested.add(holding.getTotalInvested());
            totalValue = totalValue.add(holding.getTotalValue());
            if (priceSnapshot != null && priceSnapshot.getDayReturn() != null) {
                dayReturnAmount = dayReturnAmount.add(aggregate.quantity.multiply(priceSnapshot.getDayReturn()));
            }
        }

        List<Holding> recalculatedHoldings = holdingRepository.findByUserIdOrderByIdAsc(userId);
        for (Holding holding : recalculatedHoldings) {
            BigDecimal weight = totalValue.compareTo(BigDecimal.ZERO) > 0
                    ? holding.getTotalValue().multiply(BigDecimal.valueOf(100)).divide(totalValue, 4, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            holding.setWeight(weight);
        }

        Portfolio portfolio = portfolioRepository.findByUserId(userId).orElseGet(() -> Portfolio.defaultOf(user));
        portfolio.setUser(user);
        portfolio.setTotalInvested(totalInvested.setScale(4, RoundingMode.HALF_UP));
        portfolio.setTotalValue(totalValue.setScale(4, RoundingMode.HALF_UP));
        portfolio.setTotalReturnAmount(totalValue.subtract(totalInvested).setScale(4, RoundingMode.HALF_UP));
        portfolio.setTotalReturnRate(percent(portfolio.getTotalReturnAmount(), totalInvested));
        portfolio.setDayReturnAmount(dayReturnAmount.setScale(4, RoundingMode.HALF_UP));
        BigDecimal previousValue = totalValue.subtract(dayReturnAmount);
        portfolio.setDayReturnRate(percent(dayReturnAmount, previousValue));
        portfolio.setSyncedAt(LocalDateTime.now());
        portfolioRepository.save(portfolio);
    }

    public BigDecimal currentQuantity(Long userId, Long stockSymbolId) {
        List<Trade> trades = tradeRepository.findByUserIdAndDeletedFalseOrderByTradedAtAscIdAsc(userId);
        BigDecimal quantity = BigDecimal.ZERO;
        for (Trade trade : trades) {
            if (!trade.getStockSymbol().getId().equals(stockSymbolId)) {
                continue;
            }
            if (trade.getTradeType() == TradeType.BUY) {
                quantity = quantity.add(trade.getQuantity());
            } else {
                quantity = quantity.subtract(trade.getQuantity());
            }
        }
        return quantity;
    }

    private BigDecimal percent(BigDecimal amount, BigDecimal base) {
        if (base == null || base.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return amount.multiply(BigDecimal.valueOf(100)).divide(base, 4, RoundingMode.HALF_UP);
    }

    private static class Aggregate {
        private final com.folo.stock.StockSymbol stockSymbol;
        private BigDecimal quantity = BigDecimal.ZERO;
        private BigDecimal totalInvested = BigDecimal.ZERO;
        private LocalDate firstBoughtDate;
        private LocalDate lastTradeDate;

        private Aggregate(com.folo.stock.StockSymbol stockSymbol) {
            this.stockSymbol = stockSymbol;
        }

        private void apply(Trade trade) {
            this.lastTradeDate = trade.getTradedAt().toLocalDate();
            if (trade.getTradeType() == TradeType.BUY) {
                if (firstBoughtDate == null) {
                    firstBoughtDate = trade.getTradedAt().toLocalDate();
                }
                totalInvested = totalInvested.add(trade.getQuantity().multiply(trade.getPrice()));
                quantity = quantity.add(trade.getQuantity());
                return;
            }

            if (quantity.compareTo(trade.getQuantity()) < 0) {
                throw new ApiException(ErrorCode.INSUFFICIENT_HOLDINGS);
            }

            BigDecimal averagePrice = averagePrice();
            totalInvested = totalInvested.subtract(averagePrice.multiply(trade.getQuantity()));
            quantity = quantity.subtract(trade.getQuantity());
            if (quantity.compareTo(BigDecimal.ZERO) == 0) {
                totalInvested = BigDecimal.ZERO;
            }
        }

        private BigDecimal averagePrice() {
            if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
                return BigDecimal.ZERO;
            }
            return totalInvested.divide(quantity, 6, RoundingMode.HALF_UP);
        }
    }
}
