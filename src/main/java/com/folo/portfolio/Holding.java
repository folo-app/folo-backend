package com.folo.portfolio;

import com.folo.common.entity.BaseTimeEntity;
import com.folo.stock.StockSymbol;
import com.folo.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "holdings",
        uniqueConstraints = @UniqueConstraint(name = "uk_holdings_user_symbol", columnNames = {"user_id", "stock_symbol_id"}),
        indexes = {
                @Index(name = "idx_holdings_user_id", columnList = "user_id"),
                @Index(name = "idx_holdings_symbol_id", columnList = "stock_symbol_id")
        })
public class Holding extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stock_symbol_id")
    private StockSymbol stockSymbol;

    @Column(nullable = false, precision = 19, scale = 6)
    private BigDecimal quantity;

    @Column(nullable = false, precision = 19, scale = 6)
    private BigDecimal avgPrice;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal totalInvested;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal totalValue;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal returnAmount;

    @Column(nullable = false, precision = 8, scale = 4)
    private BigDecimal returnRate;

    @Column(nullable = false, precision = 8, scale = 4)
    private BigDecimal weight;

    private LocalDate firstBoughtDate;

    private LocalDate lastTradeDate;

    private LocalDateTime calculatedAt;
}
