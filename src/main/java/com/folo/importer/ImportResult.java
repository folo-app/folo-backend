package com.folo.importer;

import com.folo.common.entity.BaseTimeEntity;
import com.folo.common.enums.TradeType;
import com.folo.common.enums.TradeVisibility;
import com.folo.stock.StockSymbol;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
@Table(name = "import_results")
public class ImportResult extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "import_job_id")
    private ImportJob importJob;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_symbol_id")
    private StockSymbol stockSymbol;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private TradeType tradeType;

    @Column(precision = 19, scale = 6)
    private BigDecimal quantity;

    @Column(precision = 19, scale = 6)
    private BigDecimal price;

    private LocalDateTime tradedAt;

    @Column(length = 300)
    private String comment;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private TradeVisibility visibility;

    @Column(nullable = false)
    private boolean valid;

    @Column(length = 500)
    private String errorMessage;

    @Column(nullable = false)
    private boolean selected;

    @Column(name = "confirmed_trade_id")
    private Long confirmedTradeId;

    private LocalDateTime confirmedAt;
}
