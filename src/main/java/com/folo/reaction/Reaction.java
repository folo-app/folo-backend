package com.folo.reaction;

import com.folo.common.entity.BaseTimeEntity;
import com.folo.common.enums.ReactionEmoji;
import com.folo.trade.Trade;
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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "reactions",
        uniqueConstraints = @UniqueConstraint(name = "uk_reactions_trade_user", columnNames = {"trade_id", "user_id"}),
        indexes = {
                @Index(name = "idx_reactions_trade_id", columnList = "trade_id"),
                @Index(name = "idx_reactions_user_id", columnList = "user_id")
        })
public class Reaction extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trade_id")
    private Trade trade;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReactionEmoji emoji;

    public Reaction(Trade trade, User user, ReactionEmoji emoji) {
        this.trade = trade;
        this.user = user;
        this.emoji = emoji;
    }
}
