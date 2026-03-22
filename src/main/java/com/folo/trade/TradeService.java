package com.folo.trade;

import com.folo.comment.CommentRepository;
import com.folo.common.enums.ReactionEmoji;
import com.folo.common.enums.TradeSource;
import com.folo.common.enums.TradeType;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
        TradeType requestedTradeType = parseTradeType(tradeType);
        String requestedTicker = normalizeTicker(ticker);
        LocalDateTime fromDateTime = from != null ? from.atStartOfDay() : null;
        LocalDateTime toExclusive = to != null ? to.plusDays(1).atStartOfDay() : null;

        Specification<Trade> specification = hasUserId(userId)
                .and(isNotDeleted())
                .and(hasTicker(requestedTicker))
                .and(hasTradeType(requestedTradeType))
                .and(tradedOnOrAfter(fromDateTime))
                .and(tradedBefore(toExclusive));

        Page<Trade> trades = tradeRepository.findAll(
                specification,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"))
        );

        List<TradeSummaryItem> items = trades.getContent().stream()
                .map(this::toSummary)
                .toList();
        return new TradeListResponse(items, trades.getTotalElements(), trades.hasNext());
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

    private @Nullable TradeType parseTradeType(@Nullable String tradeType) {
        if (tradeType == null || tradeType.isBlank()) {
            return null;
        }

        try {
            return TradeType.valueOf(tradeType.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "지원하지 않는 tradeType 입니다.");
        }
    }

    private @Nullable String normalizeTicker(@Nullable String ticker) {
        if (ticker == null || ticker.isBlank()) {
            return null;
        }
        return ticker.trim().toUpperCase(Locale.ROOT);
    }

    private Specification<Trade> hasUserId(Long userId) {
        return (root, query, builder) -> builder.equal(root.get("user").get("id"), userId);
    }

    private Specification<Trade> isNotDeleted() {
        return (root, query, builder) -> builder.isFalse(root.get("deleted"));
    }

    private Specification<Trade> hasTicker(@Nullable String ticker) {
        return (root, query, builder) -> {
            if (ticker == null) {
                return builder.conjunction();
            }

            return builder.equal(
                    builder.upper(root.join("stockSymbol").get("ticker")),
                    ticker
            );
        };
    }

    private Specification<Trade> hasTradeType(@Nullable TradeType tradeType) {
        return (root, query, builder) ->
                tradeType == null
                        ? builder.conjunction()
                        : builder.equal(root.get("tradeType"), tradeType);
    }

    private Specification<Trade> tradedOnOrAfter(@Nullable LocalDateTime fromDateTime) {
        return (root, query, builder) ->
                fromDateTime == null
                        ? builder.conjunction()
                        : builder.greaterThanOrEqualTo(root.get("tradedAt"), fromDateTime);
    }

    private Specification<Trade> tradedBefore(@Nullable LocalDateTime toExclusive) {
        return (root, query, builder) ->
                toExclusive == null
                        ? builder.conjunction()
                        : builder.lessThan(root.get("tradedAt"), toExclusive);
    }
}
