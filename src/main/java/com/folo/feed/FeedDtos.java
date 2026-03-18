package com.folo.feed;

import com.folo.common.enums.ReactionEmoji;
import com.folo.common.enums.TradeType;
import org.springframework.lang.Nullable;

import java.math.BigDecimal;
import java.util.List;

record FeedTradeUser(
        Long userId,
        String nickname,
        @Nullable String profileImage
) {
}

record FeedReaction(
        ReactionEmoji emoji,
        long count,
        boolean isMyReaction
) {
}

record FeedTradeItem(
        Long tradeId,
        FeedTradeUser user,
        String ticker,
        String name,
        String market,
        TradeType tradeType,
        BigDecimal quantity,
        BigDecimal price,
        @Nullable String comment,
        List<FeedReaction> reactions,
        long commentCount,
        String tradedAt
) {
}

record FeedResponse(
        List<FeedTradeItem> trades,
        @Nullable Long nextCursor,
        boolean hasNext
) {
}
