package com.folo.stock;

import com.folo.common.enums.AssetType;
import com.folo.common.enums.MarketType;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Objects;

@Service
public class KrxPreferredStockResolver {

    private final StockSymbolRepository stockSymbolRepository;

    public KrxPreferredStockResolver(StockSymbolRepository stockSymbolRepository) {
        this.stockSymbolRepository = stockSymbolRepository;
    }

    @Nullable
    public StockSymbol resolveBaseCommonStock(StockSymbol preferredStock) {
        if (!KrxPreferredStockSupport.isKrxPreferredStock(preferredStock)) {
            return null;
        }

        String baseTicker = KrxPreferredStockSupport.baseTickerCandidate(
                preferredStock.getTicker(),
                preferredStock.getName()
        );
        if (StringUtils.hasText(baseTicker)) {
            StockSymbol tickerMatch = stockSymbolRepository.findByMarketAndTicker(MarketType.KRX, baseTicker)
                    .filter(candidate -> isCommonStockCandidate(candidate, preferredStock))
                    .orElse(null);
            if (tickerMatch != null) {
                return tickerMatch;
            }
        }

        String baseName = KrxPreferredStockSupport.baseNameCandidate(preferredStock.getName());
        if (!StringUtils.hasText(baseName)) {
            return null;
        }

        StockSymbol exactNameMatch = stockSymbolRepository.findByMarketAndName(MarketType.KRX, baseName)
                .filter(candidate -> isCommonStockCandidate(candidate, preferredStock))
                .orElse(null);
        if (exactNameMatch != null) {
            return exactNameMatch;
        }

        return stockSymbolRepository.findByMarketAndActiveTrueAndNameStartingWith(MarketType.KRX, baseName).stream()
                .filter(candidate -> isCommonStockCandidate(candidate, preferredStock))
                .findFirst()
                .orElse(null);
    }

    private boolean isCommonStockCandidate(StockSymbol candidate, StockSymbol preferredStock) {
        return candidate != null
                && candidate.isActive()
                && candidate.getAssetType() == AssetType.STOCK
                && candidate.getMarket() == MarketType.KRX
                && !Objects.equals(candidate.getId(), preferredStock.getId())
                && !KrxPreferredStockSupport.isKrxPreferredStock(candidate);
    }
}
