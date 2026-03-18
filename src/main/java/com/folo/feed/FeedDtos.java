package com.folo.feed;

import com.folo.common.enums.ReactionEmoji;
import com.folo.common.enums.TradeType;

import java.math.BigDecimal;
import java.util.List;

record FeedTradeUser(
        Long userId,
        String nickname,
        String profileImage
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
        String comment,
        List<FeedReaction> reactions,
        long commentCount,
        String tradedAt
) {
}

record FeedResponse(
        List<FeedTradeItem> trades,
        Long nextCursor,
        boolean hasNext
) {
}
