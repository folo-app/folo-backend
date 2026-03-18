package com.folo.stock;

import com.folo.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
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
@Table(name = "price_snapshots")
public class PriceSnapshot extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stock_symbol_id")
    private StockSymbol stockSymbol;

    @Column(nullable = false, precision = 19, scale = 6)
    private BigDecimal currentPrice;

    @Column(precision = 19, scale = 6)
    private BigDecimal openPrice;

    @Column(precision = 19, scale = 6)
    private BigDecimal highPrice;

    @Column(precision = 19, scale = 6)
    private BigDecimal lowPrice;

    @Column(precision = 19, scale = 6)
    private BigDecimal dayReturn;

    @Column(precision = 8, scale = 4)
    private BigDecimal dayReturnRate;

    @Column(nullable = false)
    private LocalDateTime marketUpdatedAt;
}
