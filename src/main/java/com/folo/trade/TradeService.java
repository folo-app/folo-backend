package com.folo.trade;

import com.folo.comment.CommentRepository;
import com.folo.common.enums.ReactionEmoji;
import com.folo.common.enums.TradeSource;
import com.folo.common.exception.ApiException;
import com.folo.common.exception.ErrorCode;
import com.folo.portfolio.PortfolioProjectionService;
import com.folo.reaction.Reaction;
import com.folo.reaction.ReactionRepository;
import com.folo.stock.StockService;
import com.folo.stock.StockSymbol;
import com.folo.user.User;
import com.folo.user.UserRepository;
import org.springframework.lang.Nullable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class TradeService {

    private final TradeRepository tradeRepository;
    private final UserRepository userRepository;
    private final StockService stockService;
    private final PortfolioProjectionService portfolioProjectionService;
    private final TradeAccessService tradeAccessService;
    private final ReactionRepository reactionRepository;
    private final CommentRepository commentRepository;

    public TradeService(
            TradeRepository tradeRepository,
            UserRepository userRepository,
            StockService stockService,
            PortfolioProjectionService portfolioProjectionService,
            TradeAccessService tradeAccessService,
            ReactionRepository reactionRepository,
            CommentRepository commentRepository
    ) {
        this.tradeRepository = tradeRepository;
        this.userRepository = userRepository;
        this.stockService = stockService;
        this.portfolioProjectionService = portfolioProjectionService;
        this.tradeAccessService = tradeAccessService;
        this.reactionRepository = reactionRepository;
        this.commentRepository = commentRepository;
    }

    @Transactional
    public TradeSummaryItem create(Long userId, CreateTradeRequest request) {
        User user = userRepository.findByIdAndActiveTrue(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "사용자를 찾을 수 없습니다."));
        StockSymbol stockSymbol = stockService.getRequiredSymbol(request.market(), request.ticker());

        if (request.tradeType().name().equals("SELL")) {
            BigDecimal currentQuantity = portfolioProjectionService.currentQuantity(userId, stockSymbol.getId());
            if (currentQuantity.compareTo(request.quantity()) < 0) {
                throw new ApiException(ErrorCode.INSUFFICIENT_HOLDINGS);
            }
        }

        Trade trade = Trade.create(
                user,
                stockSymbol,
                request.tradeType(),
                request.quantity(),
                request.price(),
                request.comment(),
                request.visibility(),
                request.tradedAt() != null ? request.tradedAt() : LocalDateTime.now(),
                TradeSource.MANUAL
        );
        tradeRepository.save(trade);
        portfolioProjectionService.recalculate(userId);
        return toSummary(trade);
    }

    @Transactional(readOnly = true)
    public TradeListResponse myTrades(
            Long userId,
            @Nullable String ticker,
            @Nullable String tradeType,
            @Nullable LocalDate from,
            @Nullable LocalDate to,
            int page,
            int size
    ) {
        List<Trade> trades = tradeRepository.findByUserIdAndDeletedFalseOrderByIdDesc(userId, PageRequest.of(page, size + 1));
        List<Trade> filtered = trades.stream()
                .filter(trade -> ticker == null || trade.getStockSymbol().getTicker().equalsIgnoreCase(ticker))
                .filter(trade -> tradeType == null || trade.getTradeType().name().equalsIgnoreCase(tradeType))
                .filter(trade -> from == null || !trade.getTradedAt().toLocalDate().isBefore(from))
                .filter(trade -> to == null || !trade.getTradedAt().toLocalDate().isAfter(to))
                .sorted(Comparator.comparing(Trade::getId).reversed())
                .toList();

        boolean hasNext = filtered.size() > size;
        List<TradeSummaryItem> items = filtered.stream().limit(size).map(this::toSummary).toList();
        return new TradeListResponse(items, filtered.size(), hasNext);
    }

    @Transactional(readOnly = true)
    public TradeDetailResponse detail(Long currentUserId, Long tradeId) {
        Trade trade = tradeRepository.findByIdAndDeletedFalse(tradeId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "거래를 찾을 수 없습니다."));
        if (!tradeAccessService.canView(currentUserId, trade)) {
            throw new ApiException(ErrorCode.TRADE_NOT_VISIBLE);
        }

        List<Reaction> reactions = reactionRepository.findByTradeId(tradeId);
        Map<ReactionEmoji, List<Reaction>> grouped = reactions.stream()
                .collect(Collectors.groupingBy(Reaction::getEmoji));

        List<ReactionSummary> summaries = grouped.entrySet().stream()
                .map(entry -> new ReactionSummary(
                        entry.getKey(),
                        entry.getValue().size(),
                        entry.getValue().stream().anyMatch(reaction -> reaction.getUser().getId().equals(currentUserId))
                ))
                .toList();

        return new TradeDetailResponse(
                trade.getId(),
                new TradeUserInfo(trade.getUser().getId(), trade.getUser().getNickname(), trade.getUser().getProfileImageUrl()),
                trade.getStockSymbol().getTicker(),
                trade.getStockSymbol().getName(),
                trade.getStockSymbol().getMarket().name(),
                trade.getTradeType(),
                trade.getQuantity(),
                trade.getPrice(),
                trade.getTotalAmount(),
                trade.getComment(),
                trade.getVisibility(),
                summaries,
                commentRepository.countByTradeIdAndDeletedFalse(tradeId),
                trade.getTradedAt().toString()
        );
    }

    @Transactional
    public TradeSummaryItem update(Long userId, Long tradeId, UpdateTradeRequest request) {
        Trade trade = tradeRepository.findByIdAndDeletedFalse(tradeId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "거래를 찾을 수 없습니다."));
        if (!trade.getUser().getId().equals(userId)) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }

        trade.updatePresentation(request.comment(), request.visibility());
        return toSummary(trade);
    }

    @Transactional
    public void delete(Long userId, Long tradeId) {
        Trade trade = tradeRepository.findByIdAndDeletedFalse(tradeId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "거래를 찾을 수 없습니다."));
        if (!trade.getUser().getId().equals(userId)) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }
        trade.softDelete();
        portfolioProjectionService.recalculate(userId);
    }

    public TradeSummaryItem toSummary(Trade trade) {
        return new TradeSummaryItem(
                trade.getId(),
                trade.getStockSymbol().getTicker(),
                trade.getStockSymbol().getName(),
                trade.getTradeType(),
                trade.getQuantity(),
                trade.getPrice(),
                trade.getTotalAmount(),
                trade.getComment(),
                trade.getVisibility(),
                reactionRepository.findByTradeId(trade.getId()).size(),
                commentRepository.countByTradeIdAndDeletedFalse(trade.getId()),
                trade.getTradedAt().toString()
        );
    }
}
