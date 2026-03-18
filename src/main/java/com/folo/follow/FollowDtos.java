package com.folo.follow;

import java.util.List;
import org.springframework.lang.Nullable;

record FollowUserItem(
        Long userId,
        String nickname,
        @Nullable String profileImage,
        boolean isFollowing
) {
}

record FollowListResponse(
        List<FollowUserItem> followers,
        List<FollowUserItem> followings,
        long totalCount,
        boolean hasNext
) {
}

record FollowActionResponse(
        Long followingId,
        String nickname,
        boolean isFollowing
) {
}
