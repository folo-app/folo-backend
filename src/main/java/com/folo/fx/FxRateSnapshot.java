package com.folo.fx;

import com.folo.common.enums.CurrencyCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record FxRateSnapshot(
        CurrencyCode baseCurrency,
        CurrencyCode quoteCurrency,
        BigDecimal rate,
        LocalDateTime asOf,
        FxRateProvider provider
) {
}
