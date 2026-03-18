package com.folo.comment;

import com.folo.common.api.ApiResponse;
import com.folo.security.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/trades/{tradeId}/comments")
public class CommentController {

    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    @GetMapping
    public ApiResponse<CommentListResponse> list(
            @PathVariable Long tradeId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.success(commentService.list(SecurityUtils.currentUserId(), tradeId, page, size), "요청이 성공했습니다.");
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CreateCommentResponse> create(@PathVariable Long tradeId, @Valid @RequestBody CreateCommentRequest request) {
        return ApiResponse.success(commentService.create(SecurityUtils.currentUserId(), tradeId, request), "댓글이 작성되었습니다.");
    }

    @DeleteMapping("/{commentId}")
    public ApiResponse<Void> delete(@PathVariable Long tradeId, @PathVariable Long commentId) {
        commentService.delete(SecurityUtils.currentUserId(), commentId);
        return ApiResponse.successMessage("댓글이 삭제되었습니다.");
    }
}
