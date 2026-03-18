package com.folo.comment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

record CommentUserInfo(
        Long userId,
        String nickname,
        String profileImage
) {
}

record CommentItem(
        Long commentId,
        CommentUserInfo user,
        String content,
        boolean isMyComment,
        String createdAt
) {
}

record CommentListResponse(
        List<CommentItem> comments,
        long totalCount,
        boolean hasNext
) {
}

record CreateCommentRequest(
        @NotBlank @Size(max = 500) String content
) {
}

record CreateCommentResponse(
        Long commentId,
        String content,
        String createdAt
) {
}
