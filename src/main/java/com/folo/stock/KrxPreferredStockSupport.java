package com.folo.stock;

import com.folo.common.enums.AssetType;
import com.folo.common.enums.MarketType;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.regex.Pattern;

public final class KrxPreferredStockSupport {

    private static final Pattern KRX_PREFERRED_STOCK_NAME_PATTERN =
            Pattern.compile(".*(?:\\d+)?우(?:[BC])?(?:\\([^)]*\\))?$");

    private static final Pattern KRX_PREFERRED_STOCK_SUFFIX_PATTERN =
            Pattern.compile("(?:\\d+)?우(?:[BC])?(?:\\([^)]*\\))?$");

    private KrxPreferredStockSupport() {
    }

    public static boolean isKrxPreferredStock(@Nullable StockSymbol stockSymbol) {
        return stockSymbol != null
                && stockSymbol.getMarket() == MarketType.KRX
                && stockSymbol.getAssetType() == AssetType.STOCK
                && isPreferredStockName(stockSymbol.getName());
    }

    public static boolean isPreferredStockName(@Nullable String name) {
        return StringUtils.hasText(name)
                && KRX_PREFERRED_STOCK_NAME_PATTERN.matcher(name.trim()).matches();
    }

    @Nullable
    public static String baseTickerCandidate(@Nullable String ticker, @Nullable String name) {
        if (!StringUtils.hasText(ticker)) {
            return null;
        }

        String normalizedTicker = ticker.trim().toUpperCase(Locale.ROOT);
        if (normalizedTicker.matches("^\\d{5}[A-Z]$")) {
            return normalizedTicker.substring(0, 5) + "0";
        }

        if (normalizedTicker.matches("^\\d{6}$")
                && !normalizedTicker.endsWith("0")
                && isPreferredStockName(name)) {
            return normalizedTicker.substring(0, 5) + "0";
        }

        return null;
    }

    @Nullable
    public static String baseNameCandidate(@Nullable String name) {
        if (!isPreferredStockName(name)) {
            return null;
        }

        String baseName = KRX_PREFERRED_STOCK_SUFFIX_PATTERN.matcher(name.trim()).replaceFirst("").trim();
        return StringUtils.hasText(baseName) ? baseName : null;
    }
}
