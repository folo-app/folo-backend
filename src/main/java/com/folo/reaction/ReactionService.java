package com.folo.reaction;

import com.folo.common.enums.ReactionEmoji;
import com.folo.common.exception.ApiException;
import com.folo.common.exception.ErrorCode;
import com.folo.notification.NotificationService;
import com.folo.trade.Trade;
import com.folo.trade.TradeAccessService;
import com.folo.trade.TradeRepository;
import com.folo.user.User;
import com.folo.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ReactionService {

    private final ReactionRepository reactionRepository;
    private final TradeRepository tradeRepository;
    private final TradeAccessService tradeAccessService;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public ReactionService(
            ReactionRepository reactionRepository,
            TradeRepository tradeRepository,
            TradeAccessService tradeAccessService,
            UserRepository userRepository,
            NotificationService notificationService
    ) {
        this.reactionRepository = reactionRepository;
        this.tradeRepository = tradeRepository;
        this.tradeAccessService = tradeAccessService;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    @Transactional
    public ReactionResponse react(Long currentUserId, Long tradeId, UpdateReactionRequest request) {
        Trade trade = tradeRepository.findByIdAndDeletedFalse(tradeId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "거래를 찾을 수 없습니다."));
        if (!tradeAccessService.canInteract(currentUserId, trade)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "리액션을 남길 수 없습니다.");
        }
        User user = userRepository.findByIdAndActiveTrue(currentUserId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "사용자를 찾을 수 없습니다."));

        Reaction existing = reactionRepository.findByTradeIdAndUserId(tradeId, currentUserId).orElse(null);
        ReactionEmoji effectiveEmoji = request.emoji();
        if (existing != null && existing.getEmoji() == request.emoji()) {
            reactionRepository.delete(existing);
            effectiveEmoji = null;
        } else if (existing != null) {
            existing.setEmoji(request.emoji());
            notificationService.notifyReaction(user, trade, request.emoji());
        } else {
            reactionRepository.save(new Reaction(trade, user, request.emoji()));
            notificationService.notifyReaction(user, trade, request.emoji());
        }

        return new ReactionResponse(tradeId, effectiveEmoji, summarize(tradeId, currentUserId));
    }

    @Transactional
    public ReactionResponse remove(Long currentUserId, Long tradeId) {
        reactionRepository.findByTradeIdAndUserId(tradeId, currentUserId)
                .ifPresent(reactionRepository::delete);
        return new ReactionResponse(tradeId, null, summarize(tradeId, currentUserId));
    }

    private List<ReactionSummaryItem> summarize(Long tradeId, Long currentUserId) {
        Map<ReactionEmoji, List<Reaction>> grouped = reactionRepository.findByTradeId(tradeId).stream()
                .collect(Collectors.groupingBy(Reaction::getEmoji));

        return grouped.entrySet().stream()
                .map(entry -> new ReactionSummaryItem(
                        entry.getKey(),
                        entry.getValue().size(),
                        entry.getValue().stream().anyMatch(reaction -> reaction.getUser().getId().equals(currentUserId))
                ))
                .toList();
    }
}
