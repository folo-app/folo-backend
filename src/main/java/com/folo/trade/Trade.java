package com.folo.trade;

import com.folo.common.entity.BaseTimeEntity;
import com.folo.common.enums.TradeSource;
import com.folo.common.enums.TradeType;
import com.folo.common.enums.TradeVisibility;
import com.folo.stock.StockSymbol;
import com.folo.user.User;
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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.lang.Nullable;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "trades", indexes = {
        @Index(name = "idx_trades_user_created_at", columnList = "user_id, created_at"),
        @Index(name = "idx_trades_user_traded_at", columnList = "user_id, traded_at"),
        @Index(name = "idx_trades_symbol_id", columnList = "stock_symbol_id"),
        @Index(name = "idx_trades_visibility", columnList = "visibility")
})
public class Trade extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stock_symbol_id")
    private StockSymbol stockSymbol;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TradeType tradeType;

    @Column(nullable = false, precision = 19, scale = 6)
    private BigDecimal quantity;

    @Column(nullable = false, precision = 19, scale = 6)
    private BigDecimal price;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal totalAmount;

    @Column(length = 300)
    @Nullable
    private String comment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TradeVisibility visibility;

    @Column(nullable = false)
    private LocalDateTime tradedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TradeSource source;

    @Column(nullable = false)
    private boolean deleted;

    @Nullable
    private LocalDateTime deletedAt;

    public static Trade create(
            User user,
            StockSymbol stockSymbol,
            TradeType tradeType,
            BigDecimal quantity,
            BigDecimal price,
            @Nullable String comment,
            TradeVisibility visibility,
            LocalDateTime tradedAt,
            TradeSource source
    ) {
        Trade trade = new Trade();
        trade.user = user;
        trade.stockSymbol = stockSymbol;
        trade.tradeType = tradeType;
        trade.quantity = quantity;
        trade.price = price;
        trade.totalAmount = quantity.multiply(price);
        trade.comment = comment;
        trade.visibility = visibility;
        trade.tradedAt = tradedAt;
        trade.source = source;
        trade.deleted = false;
        return trade;
    }

    public void updatePresentation(@Nullable String comment, @Nullable TradeVisibility visibility) {
        if (comment != null) {
            this.comment = comment;
        }
        if (visibility != null) {
            this.visibility = visibility;
        }
    }

    public void softDelete() {
        this.deleted = true;
        this.deletedAt = LocalDateTime.now();
    }
}
