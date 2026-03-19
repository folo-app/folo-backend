package com.folo.stock;

import com.folo.common.entity.BaseTimeEntity;
import com.folo.common.enums.MarketType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "stock_symbol_sync_runs")
public class StockSymbolSyncRun extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private StockDataProvider provider;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MarketType market;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StockSymbolSyncScope syncScope;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StockSymbolSyncStatus status;

    @Column(length = 1000)
    private String requestCursor;

    @Column(length = 1000)
    private String nextCursor;

    @Column(nullable = false)
    private int fetchedCount;

    @Column(nullable = false)
    private int upsertedCount;

    @Column(nullable = false)
    private int deactivatedCount;

    @Column(length = 2000)
    private String errorMessage;

    @Column(nullable = false)
    private LocalDateTime startedAt;

    private LocalDateTime completedAt;
}
