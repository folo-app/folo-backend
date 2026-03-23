package com.folo.stock;

import com.folo.common.enums.MarketType;
import org.springframework.lang.Nullable;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class StockSearchAliasResolver {

    private static final Map<String, List<AliasEntry>> ALIASES = new LinkedHashMap<>();

    static {
        register("메타", alias("META", MarketType.NASDAQ));
        register("페이스북", alias("META", MarketType.NASDAQ));
        register("애플", alias("AAPL", MarketType.NASDAQ));
        register("마이크로소프트", alias("MSFT", MarketType.NASDAQ));
        register("엔비디아", alias("NVDA", MarketType.NASDAQ));
        register("구글", alias("GOOGL", MarketType.NASDAQ));
        register("알파벳", alias("GOOGL", MarketType.NASDAQ));
        register("아마존", alias("AMZN", MarketType.NASDAQ));
        register("테슬라", alias("TSLA", MarketType.NASDAQ));
        register("넷플릭스", alias("NFLX", MarketType.NASDAQ));
        register("브로드컴", alias("AVGO", MarketType.NASDAQ));
        register("뱅가드", alias("VOO", MarketType.NYSE));
        register("에스앤피500", alias("VOO", MarketType.NYSE), alias("SPY", MarketType.NYSE));
        register("나스닥100", alias("QQQ", MarketType.NASDAQ));
    }

    private StockSearchAliasResolver() {
    }

    public static List<String> resolveTickers(String query, Collection<MarketType> markets) {
        if (query == null) {
            return List.of();
        }

        List<AliasEntry> entries = ALIASES.get(normalize(query));
        if (entries == null || entries.isEmpty()) {
            return List.of();
        }

        Set<MarketType> marketSet = Set.copyOf(markets);
        return entries.stream()
                .filter(entry -> marketSet.contains(entry.market()))
                .map(AliasEntry::ticker)
                .distinct()
                .toList();
    }

    private static void register(String alias, AliasEntry... entries) {
        ALIASES.put(normalize(alias), List.of(entries));
    }

    private static AliasEntry alias(String ticker, MarketType market) {
        return new AliasEntry(ticker, market);
    }

    private static String normalize(String raw) {
        return raw.trim().toLowerCase(Locale.ROOT);
    }

    private record AliasEntry(
            String ticker,
            MarketType market
    ) {
    }
}
