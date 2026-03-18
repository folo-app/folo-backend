package com.folo.reaction;

import com.folo.common.api.ApiResponse;
import com.folo.security.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/trades/{tradeId}/reactions")
public class ReactionController {

    private final ReactionService reactionService;

    public ReactionController(ReactionService reactionService) {
        this.reactionService = reactionService;
    }

    @PostMapping
    public ApiResponse<ReactionResponse> react(@PathVariable Long tradeId, @Valid @RequestBody UpdateReactionRequest request) {
        return ApiResponse.success(reactionService.react(SecurityUtils.currentUserId(), tradeId, request), "리액션이 반영되었습니다.");
    }

    @DeleteMapping
    public ApiResponse<ReactionResponse> remove(@PathVariable Long tradeId) {
        return ApiResponse.success(reactionService.remove(SecurityUtils.currentUserId(), tradeId), "리액션이 취소되었습니다.");
    }
}
