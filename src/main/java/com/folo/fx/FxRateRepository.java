package com.folo.fx;

import com.folo.common.enums.CurrencyCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FxRateRepository extends JpaRepository<FxRate, Long> {

    Optional<FxRate> findByBaseCurrencyAndQuoteCurrency(CurrencyCode baseCurrency, CurrencyCode quoteCurrency);
}
