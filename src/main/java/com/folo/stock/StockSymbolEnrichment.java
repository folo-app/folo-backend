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
@Table(name = "stock_symbol_enrichments",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_stock_symbol_enrichments_symbol_provider",
                columnNames = {"stock_symbol_id", "provider"}
        ),
        indexes = {
                @Index(name = "idx_stock_symbol_enrichments_symbol", columnList = "stock_symbol_id"),
                @Index(name = "idx_stock_symbol_enrichments_provider_last_enriched_at", columnList = "provider, last_enriched_at")
        })
public class StockSymbolEnrichment extends BaseTimeEntity {

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
    private String sectorNameRaw;

    @Nullable
    @Column(length = 160)
    private String industryNameRaw;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private StockClassificationScheme classificationScheme;

    @Nullable
    @Column(length = 60)
    private String sourcePayloadVersion;

    @Column(nullable = false)
    private LocalDateTime lastEnrichedAt;
}
