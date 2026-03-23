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

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "stock_brand_assets",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_stock_brand_assets_symbol",
                columnNames = {"stock_symbol_id"}
        ),
        indexes = {
                @Index(name = "idx_stock_brand_assets_stock_symbol_id", columnList = "stock_symbol_id"),
                @Index(name = "idx_stock_brand_assets_last_synced_at", columnList = "last_synced_at")
        })
public class StockBrandAsset extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stock_symbol_id")
    private StockSymbol stockSymbol;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private StockDataProvider provider;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StockBrandAssetSourceType sourceType;

    @Nullable
    @Column(length = 1000)
    private String sourceUrl;

    @Column(nullable = false, length = 500)
    private String storagePath;

    @Column(nullable = false, length = 500)
    private String publicUrl;

    @Nullable
    @Column(length = 100)
    private String contentType;

    @Column(nullable = false)
    private LocalDateTime lastSyncedAt;
}
