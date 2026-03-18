package com.folo.stock;

import com.folo.common.enums.MarketType;
import com.folo.common.exception.ApiException;
import com.folo.common.exception.ErrorCode;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class StockService {

    private final StockSymbolRepository stockSymbolRepository;
    private final PriceSnapshotRepository priceSnapshotRepository;

    public StockService(StockSymbolRepository stockSymbolRepository, PriceSnapshotRepository priceSnapshotRepository) {
        this.stockSymbolRepository = stockSymbolRepository;
        this.priceSnapshotRepository = priceSnapshotRepository;
    }

    @Transactional(readOnly = true)
    public StockSearchResponse search(@Nullable String q, @Nullable String market) {
        if (q == null || q.trim().length() < 2) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "검색어는 2자 이상이어야 합니다.");
        }

        List<StockSymbol> stocks = market == null || market.equalsIgnoreCase("ALL")
                ? stockSymbolRepository.findTop20ByNameContainingIgnoreCaseOrTickerContainingIgnoreCase(q.trim(), q.trim())
                : stockSymbolRepository.findTop20ByMarketAndNameContainingIgnoreCaseOrMarketAndTickerContainingIgnoreCase(
                MarketType.valueOf(market.toUpperCase()),
                q.trim(),
                MarketType.valueOf(market.toUpperCase()),
                q.trim()
        );

        return new StockSearchResponse(stocks.stream()
                .map(stock -> {
                    PriceSnapshot snapshot = priceSnapshotRepository.findByStockSymbolId(stock.getId()).orElse(null);
                    return new StockSearchItem(
                            stock.getTicker(),
                            stock.getName(),
                            stock.getMarket(),
                            snapshot != null ? snapshot.getCurrentPrice() : BigDecimal.ZERO,
                            snapshot != null ? snapshot.getDayReturnRate() : BigDecimal.ZERO
                    );
                })
                .toList());
    }

    @Transactional(readOnly = true)
    public StockPriceResponse getPrice(String ticker, String market) {
        StockSymbol stock = stockSymbolRepository.findByMarketAndTicker(MarketType.valueOf(market.toUpperCase()), ticker)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "종목을 찾을 수 없습니다."));
        PriceSnapshot snapshot = priceSnapshotRepository.findByStockSymbolId(stock.getId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "시세 정보를 찾을 수 없습니다."));

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
                snapshot.getMarketUpdatedAt().toString()
        );
    }

    @Transactional(readOnly = true)
    public StockSymbol getRequiredSymbol(MarketType market, String ticker) {
        return stockSymbolRepository.findByMarketAndTicker(market, ticker)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "종목을 찾을 수 없습니다."));
    }
}
