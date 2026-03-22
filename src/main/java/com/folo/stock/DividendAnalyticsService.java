package com.folo.stock;

import com.folo.common.exception.ApiException;
import com.folo.common.exception.ErrorCode;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class DividendAnalyticsService {

    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);
    private static final int DIVIDEND_MONTH_LOOKBACK_MONTHS = 36;
    private static final int DIVIDEND_YIELD_LOOKBACK_MONTHS = 12;

    private final StockSymbolRepository stockSymbolRepository;
    private final StockDividendEventRepository stockDividendEventRepository;
    private final PriceSnapshotRepository priceSnapshotRepository;

    public DividendAnalyticsService(
            StockSymbolRepository stockSymbolRepository,
            StockDividendEventRepository stockDividendEventRepository,
            PriceSnapshotRepository priceSnapshotRepository
    ) {
        this.stockSymbolRepository = stockSymbolRepository;
        this.stockDividendEventRepository = stockDividendEventRepository;
        this.priceSnapshotRepository = priceSnapshotRepository;
    }

    @Transactional
    public void refreshAnalytics(Long stockSymbolId) {
        StockSymbol stockSymbol = stockSymbolRepository.findById(stockSymbolId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "종목을 찾을 수 없습니다."));
        List<StockDividendEvent> events = stockDividendEventRepository.findAllByStockSymbolId(stockSymbolId);
        PriceSnapshot snapshot = priceSnapshotRepository.findByStockSymbolId(stockSymbolId).orElse(null);

        stockSymbol.setAnnualDividendYield(calculateAnnualDividendYield(events, snapshot));
        stockSymbol.setDividendMonthsCsv(calculateDividendMonthsCsv(events));
        stockSymbolRepository.save(stockSymbol);
    }

    @Transactional
    public void refreshAnalyticsBatch(Collection<Long> stockSymbolIds) {
        if (stockSymbolIds == null || stockSymbolIds.isEmpty()) {
            return;
        }

        stockSymbolIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .forEach(this::refreshAnalytics);
    }

    @Nullable
    BigDecimal calculateAnnualDividendYield(List<StockDividendEvent> events, @Nullable PriceSnapshot snapshot) {
        if (snapshot == null
                || snapshot.getCurrentPrice() == null
                || snapshot.getCurrentPrice().compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }

        LocalDate cutoff = LocalDate.now().minusMonths(DIVIDEND_YIELD_LOOKBACK_MONTHS);
        BigDecimal trailingDividendAmount = events.stream()
                .filter(this::isCashDividendEvent)
                .filter(event -> event.getCashAmount() != null)
                .filter(event -> {
                    LocalDate representativeDate = resolveRepresentativeDate(event);
                    return representativeDate != null && !representativeDate.isBefore(cutoff);
                })
                .map(StockDividendEvent::getCashAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (trailingDividendAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }

        BigDecimal annualDividendYield = trailingDividendAmount
                .multiply(ONE_HUNDRED)
                .divide(snapshot.getCurrentPrice(), 4, RoundingMode.HALF_UP);

        if (annualDividendYield.compareTo(BigDecimal.ZERO) <= 0 || annualDividendYield.compareTo(ONE_HUNDRED) > 0) {
            return null;
        }

        return annualDividendYield;
    }

    @Nullable
    String calculateDividendMonthsCsv(List<StockDividendEvent> events) {
        LocalDate cutoff = LocalDate.now().minusMonths(DIVIDEND_MONTH_LOOKBACK_MONTHS);
        Map<Integer, Integer> monthCounts = new LinkedHashMap<>();

        for (StockDividendEvent event : events) {
            if (!isCashDividendEvent(event)) {
                continue;
            }

            LocalDate representativeDate = resolveRepresentativeDate(event);
            if (representativeDate == null || representativeDate.isBefore(cutoff)) {
                continue;
            }

            monthCounts.merge(representativeDate.getMonthValue(), 1, Integer::sum);
        }

        List<Integer> recurringMonths = monthCounts.entrySet().stream()
                .filter(entry -> entry.getValue() >= 2)
                .map(Map.Entry::getKey)
                .sorted(Comparator.naturalOrder())
                .toList();

        if (recurringMonths.isEmpty()) {
            return null;
        }

        List<String> tokens = new ArrayList<>(recurringMonths.size());
        for (Integer month : recurringMonths) {
            tokens.add(String.valueOf(month));
        }
        return String.join(",", tokens);
    }

    private boolean isCashDividendEvent(StockDividendEvent event) {
        return event.getEventType() == DividendEventType.CASH
                || event.getEventType() == DividendEventType.SPECIAL_CASH;
    }

    @Nullable
    private LocalDate resolveRepresentativeDate(StockDividendEvent event) {
        if (event.getPayDate() != null) {
            return event.getPayDate();
        }
        if (event.getExDividendDate() != null) {
            return event.getExDividendDate();
        }
        if (event.getRecordDate() != null) {
            return event.getRecordDate();
        }
        return event.getDeclaredDate();
    }
}
