package com.folo.fx;

import com.folo.common.enums.CurrencyCode;

public interface FxRateSyncProvider {

    FxRateProvider provider();

    boolean isConfigured();

    FxRateSnapshot fetchRate(CurrencyCode baseCurrency, CurrencyCode quoteCurrency);
}
