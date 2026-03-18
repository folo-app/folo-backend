package com.folo.feed;

import com.folo.comment.CommentRepository;
import com.folo.common.enums.ReactionEmoji;
import com.folo.follow.FollowRepository;
import com.folo.reaction.Reaction;
import com.folo.reaction.ReactionRepository;
import com.folo.trade.Trade;
import com.folo.trade.TradeAccessService;
import com.folo.trade.TradeRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class FeedService {

    private final FollowRepository followRepository;
    private final TradeRepository tradeRepository;
    private final TradeAccessService tradeAccessService;
    private final ReactionRepository reactionRepository;
    private final CommentRepository commentRepository;

    public FeedService(
            FollowRepository followRepository,
            TradeRepository tradeRepository,
            TradeAccessService tradeAccessService,
            ReactionRepository reactionRepository,
            CommentRepository commentRepository
    ) {
        this.followRepository = followRepository;
        this.tradeRepository = tradeRepository;
        this.tradeAccessService = tradeAccessService;
        this.reactionRepository = reactionRepository;
        this.commentRepository = commentRepository;
    }

    @Transactional(readOnly = true)
    public FeedResponse friendsFeed(Long currentUserId, Long cursor, int size) {
        List<Long> userIds = followRepository.findByFollowerId(currentUserId).stream()
                .map(follow -> follow.getFollowing().getId())
                .toList();

        if (userIds.isEmpty()) {
            return new FeedResponse(List.of(), null, false);
        }

        List<Trade> trades = cursor == null
                ? tradeRepository.findByUserIdInAndDeletedFalseOrderByIdDesc(userIds, PageRequest.of(0, size + 1))
                : tradeRepository.findByUserIdInAndDeletedFalseAndIdLessThanOrderByIdDesc(userIds, cursor, PageRequest.of(0, size + 1));

        List<Trade> visibleTrades = trades.stream()
                .filter(trade -> tradeAccessService.visibleOnFeed(currentUserId, trade))
                .limit(size + 1L)
                .toList();

        return toFeedResponse(currentUserId, visibleTrades, size);
    }

    @Transactional(readOnly = true)
    public FeedResponse userFeed(Long currentUserId, Long targetUserId, Long cursor, int size) {
        List<Trade> trades = cursor == null
                ? tradeRepository.findByUserIdAndDeletedFalseOrderByIdDesc(targetUserId, PageRequest.of(0, size + 1))
                : tradeRepository.findByUserIdAndDeletedFalseAndIdLessThanOrderByIdDesc(targetUserId, cursor, PageRequest.of(0, size + 1));

        List<Trade> visibleTrades = trades.stream()
                .filter(trade -> tradeAccessService.visibleOnFeed(currentUserId, trade))
                .limit(size + 1L)
                .toList();

        return toFeedResponse(currentUserId, visibleTrades, size);
    }

    private FeedResponse toFeedResponse(Long currentUserId, List<Trade> trades, int size) {
        boolean hasNext = trades.size() > size;
        List<Trade> content = hasNext ? trades.subList(0, size) : trades;

        List<FeedTradeItem> items = content.stream().map(trade -> {
            List<Reaction> reactions = reactionRepository.findByTradeId(trade.getId());
            Map<ReactionEmoji, List<Reaction>> grouped = reactions.stream()
                    .collect(Collectors.groupingBy(Reaction::getEmoji));
            List<FeedReaction> feedReactions = grouped.entrySet().stream()
                    .map(entry -> new FeedReaction(
                            entry.getKey(),
                            entry.getValue().size(),
                            entry.getValue().stream().anyMatch(reaction -> reaction.getUser().getId().equals(currentUserId))
                    ))
                    .toList();

            return new FeedTradeItem(
                    trade.getId(),
                    new FeedTradeUser(trade.getUser().getId(), trade.getUser().getNickname(), trade.getUser().getProfileImageUrl()),
                    trade.getStockSymbol().getTicker(),
                    trade.getStockSymbol().getName(),
                    trade.getStockSymbol().getMarket().name(),
                    trade.getTradeType(),
                    trade.getQuantity(),
                    trade.getPrice(),
                    trade.getComment(),
                    feedReactions,
                    commentRepository.countByTradeIdAndDeletedFalse(trade.getId()),
                    trade.getTradedAt().toString()
            );
        }).toList();

        Long nextCursor = hasNext && !content.isEmpty() ? content.get(content.size() - 1).getId() : null;
        return new FeedResponse(items, nextCursor, hasNext);
    }
}
