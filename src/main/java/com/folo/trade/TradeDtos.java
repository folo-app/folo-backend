package com.folo.trade;

import com.folo.common.enums.MarketType;
import com.folo.common.enums.ReactionEmoji;
import com.folo.common.enums.TradeType;
import com.folo.common.enums.TradeVisibility;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

record CreateTradeRequest(
        @NotBlank String ticker,
        @NotNull MarketType market,
        @NotNull TradeType tradeType,
        @NotNull @DecimalMin(value = "0.000001") BigDecimal quantity,
        @NotNull @DecimalMin(value = "0.0") BigDecimal price,
        @Size(max = 300) String comment,
        @NotNull TradeVisibility visibility,
        LocalDateTime tradedAt
) {
}

record UpdateTradeRequest(
        @Size(max = 300) String comment,
        TradeVisibility visibility
) {
}

record TradeSummaryItem(
        Long tradeId,
        String ticker,
        String name,
        TradeType tradeType,
        BigDecimal quantity,
        BigDecimal price,
        BigDecimal totalAmount,
        String comment,
        TradeVisibility visibility,
        long reactionCount,
        long commentCount,
        String tradedAt
) {
}

record TradeListResponse(
        List<TradeSummaryItem> trades,
        long totalCount,
        boolean hasNext
) {
}

record TradeUserInfo(
        Long userId,
        String nickname,
        String profileImage
) {
}

record ReactionSummary(
        ReactionEmoji emoji,
        long count,
        boolean isMyReaction
) {
}

record TradeDetailResponse(
        Long tradeId,
        TradeUserInfo user,
        String ticker,
        String name,
        String market,
        TradeType tradeType,
        BigDecimal quantity,
        BigDecimal price,
        BigDecimal totalAmount,
        String comment,
        TradeVisibility visibility,
        List<ReactionSummary> reactions,
        long commentCount,
        String tradedAt
) {
}
