package com.folo.common.enums;

import org.springframework.lang.Nullable;

public enum CurrencyCode {
    KRW,
    USD;

    public static CurrencyCode fromMarket(MarketType market) {
        return market == MarketType.KRX ? KRW : USD;
    }

    public static CurrencyCode fromMarketAndRaw(MarketType market, @Nullable String rawCurrencyCode) {
        if (rawCurrencyCode == null || rawCurrencyCode.isBlank()) {
            return fromMarket(market);
        }
        try {
            return CurrencyCode.valueOf(rawCurrencyCode.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            return fromMarket(market);
        }
    }

    public static CurrencyCode fromRaw(@Nullable String rawCurrencyCode) {
        if (rawCurrencyCode == null || rawCurrencyCode.isBlank()) {
            return null;
        }
        try {
            return CurrencyCode.valueOf(rawCurrencyCode.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
