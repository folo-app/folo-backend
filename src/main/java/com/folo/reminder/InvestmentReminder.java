package com.folo.reminder;

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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "investment_reminders", indexes = {
        @Index(name = "idx_reminders_user_active", columnList = "user_id, active"),
        @Index(name = "idx_reminders_next_date", columnList = "next_reminder_date")
})
public class InvestmentReminder extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stock_symbol_id")
    private StockSymbol stockSymbol;

    @Column(precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false)
    private Integer dayOfMonth;

    @Column(nullable = false)
    private boolean active;

    private LocalDate nextReminderDate;
}
