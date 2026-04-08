package com.folo.fx;

import com.folo.common.enums.CurrencyCode;
import com.folo.common.exception.ApiException;
import com.folo.common.exception.ErrorCode;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class FxRateService {

    private static final int MONEY_SCALE = 4;

    private final FxRateRepository fxRateRepository;
    private final List<FxRateSyncProvider> providers;

    public FxRateService(
            FxRateRepository fxRateRepository,
            List<FxRateSyncProvider> providers
    ) {
        this.fxRateRepository = fxRateRepository;
        this.providers = providers;
    }

    @Transactional
    public FxRate syncUsdKrw() {
        return syncPair(CurrencyCode.USD, CurrencyCode.KRW);
    }

    @Transactional
    public FxRate syncPair(CurrencyCode baseCurrency, CurrencyCode quoteCurrency) {
        RuntimeException lastException = null;

        for (FxRateSyncProvider provider : providers) {
            if (!provider.isConfigured()) {
                continue;
            }
            try {
                FxRateSnapshot snapshot = provider.fetchRate(baseCurrency, quoteCurrency);
                return upsert(snapshot);
            } catch (RuntimeException exception) {
                lastException = exception;
            }
        }

        if (lastException != null) {
            throw lastException;
        }
        throw new IllegalStateException("No FX provider is configured");
    }

    @Transactional(readOnly = true, noRollbackFor = ApiException.class)
    public ConversionResult convert(
            @Nullable BigDecimal amount,
            CurrencyCode sourceCurrency,
            CurrencyCode targetCurrency
    ) {
        BigDecimal normalizedAmount = amount == null ? BigDecimal.ZERO : amount;
        if (sourceCurrency == targetCurrency) {
            return new ConversionResult(normalizedAmount.setScale(MONEY_SCALE, RoundingMode.HALF_UP), null, false);
        }

        FxRate fxRate = loadRate(sourceCurrency, targetCurrency);
        BigDecimal converted = normalizedAmount.multiply(fxRate.getRate())
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        boolean stale = fxRate.getAsOf().isBefore(LocalDateTime.now().minusHours(24));
        return new ConversionResult(converted, fxRate.getAsOf(), stale);
    }

    private FxRate loadRate(CurrencyCode sourceCurrency, CurrencyCode targetCurrency) {
        return fxRateRepository.findByBaseCurrencyAndQuoteCurrency(sourceCurrency, targetCurrency)
                .orElseGet(() -> fxRateRepository.findByBaseCurrencyAndQuoteCurrency(targetCurrency, sourceCurrency)
                        .map(this::deriveInverseRate)
                        .orElseThrow(() -> new ApiException(ErrorCode.INTERNAL_ERROR, "환율 정보를 불러올 수 없습니다.")));
    }

    private FxRate upsert(FxRateSnapshot snapshot) {
        FxRate directRate = upsertRate(
                snapshot.baseCurrency(),
                snapshot.quoteCurrency(),
                snapshot.rate(),
                snapshot.asOf(),
                snapshot.provider()
        );
        upsertRate(
                snapshot.quoteCurrency(),
                snapshot.baseCurrency(),
                BigDecimal.ONE.divide(snapshot.rate(), 8, RoundingMode.HALF_UP),
                snapshot.asOf(),
                snapshot.provider()
        );
        return directRate;
    }

    private FxRate upsertRate(
            CurrencyCode baseCurrency,
            CurrencyCode quoteCurrency,
            BigDecimal rate,
            LocalDateTime asOf,
            FxRateProvider provider
    ) {
        FxRate fxRate = fxRateRepository.findByBaseCurrencyAndQuoteCurrency(baseCurrency, quoteCurrency)
                .orElseGet(FxRate::new);

        fxRate.setBaseCurrency(baseCurrency);
        fxRate.setQuoteCurrency(quoteCurrency);
        fxRate.setRate(rate.setScale(8, RoundingMode.HALF_UP));
        fxRate.setAsOf(asOf);
        fxRate.setProvider(provider);
        fxRate.setLastSyncedAt(LocalDateTime.now());
        return fxRateRepository.save(fxRate);
    }

    private FxRate deriveInverseRate(FxRate inverseRate) {
        FxRate derivedRate = new FxRate();
        derivedRate.setBaseCurrency(inverseRate.getQuoteCurrency());
        derivedRate.setQuoteCurrency(inverseRate.getBaseCurrency());
        derivedRate.setRate(BigDecimal.ONE.divide(inverseRate.getRate(), 8, RoundingMode.HALF_UP));
        derivedRate.setAsOf(inverseRate.getAsOf());
        derivedRate.setProvider(inverseRate.getProvider());
        derivedRate.setLastSyncedAt(inverseRate.getLastSyncedAt());
        return derivedRate;
    }

    public record ConversionResult(
            BigDecimal amount,
            @Nullable LocalDateTime asOf,
            boolean stale
    ) {
    }
}
