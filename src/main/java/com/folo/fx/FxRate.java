package com.folo.fx;

import com.folo.common.entity.BaseTimeEntity;
import com.folo.common.enums.CurrencyCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "fx_rates",
        uniqueConstraints = @UniqueConstraint(name = "uk_fx_rates_pair", columnNames = {"base_currency", "quote_currency"}),
        indexes = @Index(name = "idx_fx_rates_pair", columnList = "base_currency, quote_currency"))
public class FxRate extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private CurrencyCode baseCurrency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private CurrencyCode quoteCurrency;

    @Column(nullable = false, precision = 19, scale = 8)
    private BigDecimal rate;

    @Column(nullable = false)
    private LocalDateTime asOf;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private FxRateProvider provider;

    @Column(nullable = false)
    private LocalDateTime lastSyncedAt;
}
