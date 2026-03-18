package com.folo.portfolio;

import com.folo.common.entity.BaseTimeEntity;
import com.folo.user.User;
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
@Table(name = "portfolios")
public class Portfolio extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal totalInvested;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal totalValue;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal totalReturnAmount;

    @Column(nullable = false, precision = 8, scale = 4)
    private BigDecimal totalReturnRate;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal dayReturnAmount;

    @Column(nullable = false, precision = 8, scale = 4)
    private BigDecimal dayReturnRate;

    @Column(nullable = false, length = 10)
    private String displayCurrency;

    private LocalDateTime syncedAt;

    public static Portfolio defaultOf(User user) {
        Portfolio portfolio = new Portfolio();
        portfolio.user = user;
        portfolio.totalInvested = BigDecimal.ZERO;
        portfolio.totalValue = BigDecimal.ZERO;
        portfolio.totalReturnAmount = BigDecimal.ZERO;
        portfolio.totalReturnRate = BigDecimal.ZERO;
        portfolio.dayReturnAmount = BigDecimal.ZERO;
        portfolio.dayReturnRate = BigDecimal.ZERO;
        portfolio.displayCurrency = "KRW";
        return portfolio;
    }
}
