package com.folo.feed;

import com.folo.common.api.ApiResponse;
import com.folo.security.SecurityUtils;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/feed")
public class FeedController {

    private final FeedService feedService;

    public FeedController(FeedService feedService) {
        this.feedService = feedService;
    }

    @GetMapping
    public ApiResponse<FeedResponse> feed(
            @Nullable @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.success(feedService.friendsFeed(SecurityUtils.currentUserId(), cursor, size), "요청이 성공했습니다.");
    }

    @GetMapping("/{userId}")
    public ApiResponse<FeedResponse> userFeed(
            @PathVariable Long userId,
            @Nullable @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.success(feedService.userFeed(SecurityUtils.currentUserId(), userId, cursor, size), "요청이 성공했습니다.");
    }
}
