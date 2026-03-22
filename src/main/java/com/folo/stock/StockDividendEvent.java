package com.folo.stock;

import com.folo.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.lang.Nullable;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "stock_dividend_events",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_stock_dividend_events_provider_dedupe",
                columnNames = {"provider", "dedupe_key"}
        ),
        indexes = {
                @Index(name = "idx_stock_dividend_events_symbol_pay_date", columnList = "stock_symbol_id, pay_date"),
                @Index(name = "idx_stock_dividend_events_symbol_ex_dividend_date", columnList = "stock_symbol_id, ex_dividend_date"),
                @Index(name = "idx_stock_dividend_events_symbol_provider", columnList = "stock_symbol_id, provider")
        })
public class StockDividendEvent extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stock_symbol_id")
    private StockSymbol stockSymbol;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private StockDataProvider provider;

    @Nullable
    @Column(length = 120)
    private String sourceEventId;

    @Column(nullable = false, length = 64, name = "dedupe_key")
    private String dedupeKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DividendEventType eventType;

    @Nullable
    private LocalDate declaredDate;

    @Nullable
    private LocalDate exDividendDate;

    @Nullable
    private LocalDate recordDate;

    @Nullable
    private LocalDate payDate;

    @Nullable
    @Column(precision = 19, scale = 6)
    private BigDecimal cashAmount;

    @Nullable
    @Column(length = 10)
    private String currencyCode;

    @Nullable
    @Column(length = 30)
    private String frequencyRaw;
}
