package com.folo.follow;

import java.util.List;

record FollowUserItem(
        Long userId,
        String nickname,
        String profileImage,
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
