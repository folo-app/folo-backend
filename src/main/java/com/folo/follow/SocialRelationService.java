package com.folo.follow;

import org.springframework.stereotype.Service;

@Service
public class SocialRelationService {

    private final FollowRepository followRepository;

    public SocialRelationService(FollowRepository followRepository) {
        this.followRepository = followRepository;
    }

    public boolean isFollowing(Long followerId, Long followingId) {
        return followRepository.existsByFollowerIdAndFollowingId(followerId, followingId);
    }

    public boolean isMutualFollow(Long userId, Long otherUserId) {
        return isFollowing(userId, otherUserId) && isFollowing(otherUserId, userId);
    }

    public long followerCount(Long userId) {
        return followRepository.countByFollowingId(userId);
    }

    public long followingCount(Long userId) {
        return followRepository.countByFollowerId(userId);
    }
}
