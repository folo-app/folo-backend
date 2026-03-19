package com.folo.stock;

import com.folo.common.entity.BaseTimeEntity;
import com.folo.common.enums.AssetType;
import com.folo.common.enums.MarketType;
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

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "stock_symbols",
        uniqueConstraints = @UniqueConstraint(name = "uk_stock_symbols_market_ticker", columnNames = {"market", "ticker"}),
        indexes = {
                @Index(name = "idx_stock_symbols_name", columnList = "name"),
                @Index(name = "idx_stock_symbols_ticker", columnList = "ticker")
        })
public class StockSymbol extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MarketType market;

    @Column(nullable = false, length = 30)
    private String ticker;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AssetType assetType;

    @Column(nullable = false)
    private boolean active;

    @Column(length = 20)
    private String primaryExchangeCode;

    @Column(length = 10)
    private String currencyCode;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private StockDataProvider sourceProvider;

    @Column(length = 100)
    private String sourceIdentifier;

    @Column
    private LocalDateTime lastMasterSyncedAt;
}
