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
@Table(name = "stock_issuer_profiles",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_stock_issuer_profiles_symbol_provider",
                        columnNames = {"stock_symbol_id", "provider"}
                ),
                @UniqueConstraint(
                        name = "uk_stock_issuer_profiles_provider_corp_code",
                        columnNames = {"provider", "corp_code"}
                )
        },
        indexes = {
                @Index(name = "idx_stock_issuer_profiles_stock_symbol_id", columnList = "stock_symbol_id"),
                @Index(name = "idx_stock_issuer_profiles_induty_code", columnList = "induty_code"),
                @Index(name = "idx_stock_issuer_profiles_last_synced_at", columnList = "last_synced_at")
        })
public class StockIssuerProfile extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stock_symbol_id")
    private StockSymbol stockSymbol;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private StockDataProvider provider;

    @Column(nullable = false, length = 8)
    private String corpCode;

    @Column(nullable = false, length = 200)
    private String corpName;

    @Column(nullable = false, length = 12)
    private String stockCode;

    @Nullable
    @Column(length = 10)
    private String corpCls;

    @Nullable
    @Column(length = 500)
    private String hmUrl;

    @Nullable
    @Column(length = 500)
    private String irUrl;

    @Nullable
    @Column(length = 20)
    private String indutyCode;

    @Nullable
    @Column(length = 60)
    private String sourcePayloadVersion;

    @Column(nullable = false)
    private LocalDateTime lastSyncedAt;
}
