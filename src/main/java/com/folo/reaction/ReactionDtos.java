package com.folo.reaction;

import com.folo.common.enums.ReactionEmoji;
import jakarta.validation.constraints.NotNull;

import java.util.List;

record UpdateReactionRequest(
        @NotNull ReactionEmoji emoji
) {
}

record ReactionSummaryItem(
        ReactionEmoji emoji,
        long count,
        boolean isMyReaction
) {
}

record ReactionResponse(
        Long tradeId,
        ReactionEmoji emoji,
        List<ReactionSummaryItem> reactions
) {
}
