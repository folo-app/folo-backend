package com.folo.comment;

import com.folo.common.exception.ApiException;
import com.folo.common.exception.ErrorCode;
import com.folo.notification.NotificationService;
import com.folo.trade.Trade;
import com.folo.trade.TradeAccessService;
import com.folo.trade.TradeRepository;
import com.folo.user.User;
import com.folo.user.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommentService {

    private final CommentRepository commentRepository;
    private final TradeRepository tradeRepository;
    private final TradeAccessService tradeAccessService;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public CommentService(
            CommentRepository commentRepository,
            TradeRepository tradeRepository,
            TradeAccessService tradeAccessService,
            UserRepository userRepository,
            NotificationService notificationService
    ) {
        this.commentRepository = commentRepository;
        this.tradeRepository = tradeRepository;
        this.tradeAccessService = tradeAccessService;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    @Transactional(readOnly = true)
    public CommentListResponse list(Long currentUserId, Long tradeId, int page, int size) {
        Trade trade = tradeRepository.findByIdAndDeletedFalse(tradeId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "거래를 찾을 수 없습니다."));
        if (!tradeAccessService.canView(currentUserId, trade)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "댓글을 조회할 수 없습니다.");
        }

        Page<Comment> comments = commentRepository.findByTradeIdAndDeletedFalseOrderByCreatedAtAsc(tradeId, PageRequest.of(page, size));
        return new CommentListResponse(
                comments.getContent().stream()
                        .map(comment -> new CommentItem(
                                comment.getId(),
                                new CommentUserInfo(comment.getUser().getId(), comment.getUser().getNickname(), comment.getUser().getProfileImageUrl()),
                                comment.getContent(),
                                comment.getUser().getId().equals(currentUserId),
                                comment.getCreatedAt().toString()
                        ))
                        .toList(),
                comments.getTotalElements(),
                comments.hasNext()
        );
    }

    @Transactional
    public CreateCommentResponse create(Long currentUserId, Long tradeId, CreateCommentRequest request) {
        Trade trade = tradeRepository.findByIdAndDeletedFalse(tradeId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "거래를 찾을 수 없습니다."));
        if (!tradeAccessService.canInteract(currentUserId, trade)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "댓글을 작성할 수 없습니다.");
        }
        User user = userRepository.findByIdAndActiveTrue(currentUserId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "사용자를 찾을 수 없습니다."));

        Comment comment = commentRepository.save(new Comment(trade, user, request.content()));
        notificationService.notifyComment(user, trade, comment);
        return new CreateCommentResponse(comment.getId(), comment.getContent(), comment.getCreatedAt().toString());
    }

    @Transactional
    public void delete(Long currentUserId, Long commentId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "댓글을 찾을 수 없습니다."));
        if (!comment.getUser().getId().equals(currentUserId)) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }
        comment.softDelete();
    }
}
