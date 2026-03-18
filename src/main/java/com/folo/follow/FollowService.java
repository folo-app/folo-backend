package com.folo.follow;

import com.folo.common.exception.ApiException;
import com.folo.common.exception.ErrorCode;
import com.folo.notification.NotificationService;
import com.folo.user.User;
import com.folo.user.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FollowService {

    private final FollowRepository followRepository;
    private final UserRepository userRepository;
    private final SocialRelationService socialRelationService;
    private final NotificationService notificationService;

    public FollowService(
            FollowRepository followRepository,
            UserRepository userRepository,
            SocialRelationService socialRelationService,
            NotificationService notificationService
    ) {
        this.followRepository = followRepository;
        this.userRepository = userRepository;
        this.socialRelationService = socialRelationService;
        this.notificationService = notificationService;
    }

    @Transactional
    public FollowActionResponse follow(Long currentUserId, Long targetUserId) {
        if (currentUserId.equals(targetUserId)) {
            throw new ApiException(ErrorCode.CANNOT_FOLLOW_SELF);
        }
        if (followRepository.existsByFollowerIdAndFollowingId(currentUserId, targetUserId)) {
            throw new ApiException(ErrorCode.DUPLICATE_FOLLOW, "이미 팔로우 중입니다.");
        }

        User me = userRepository.findByIdAndActiveTrue(currentUserId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "사용자를 찾을 수 없습니다."));
        User target = userRepository.findByIdAndActiveTrue(targetUserId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "사용자를 찾을 수 없습니다."));

        followRepository.save(new Follow(me, target));
        notificationService.notifyFollow(me, target);
        return new FollowActionResponse(target.getId(), target.getNickname(), true);
    }

    @Transactional
    public void unfollow(Long currentUserId, Long targetUserId) {
        Follow follow = followRepository.findByFollowerIdAndFollowingId(currentUserId, targetUserId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "팔로우 관계를 찾을 수 없습니다."));
        followRepository.delete(follow);
    }

    @Transactional(readOnly = true)
    public FollowListResponse followers(Long currentUserId, int page, int size) {
        Page<Follow> followers = followRepository.findByFollowingId(currentUserId, PageRequest.of(page, size));
        return new FollowListResponse(
                followers.getContent().stream()
                        .map(follow -> new FollowUserItem(
                                follow.getFollower().getId(),
                                follow.getFollower().getNickname(),
                                follow.getFollower().getProfileImageUrl(),
                                socialRelationService.isFollowing(currentUserId, follow.getFollower().getId())
                        ))
                        .toList(),
                null,
                followers.getTotalElements(),
                followers.hasNext()
        );
    }

    @Transactional(readOnly = true)
    public FollowListResponse followings(Long currentUserId, int page, int size) {
        Page<Follow> followings = followRepository.findByFollowerId(currentUserId, PageRequest.of(page, size));
        return new FollowListResponse(
                null,
                followings.getContent().stream()
                        .map(follow -> new FollowUserItem(
                                follow.getFollowing().getId(),
                                follow.getFollowing().getNickname(),
                                follow.getFollowing().getProfileImageUrl(),
                                true
                        ))
                        .toList(),
                followings.getTotalElements(),
                followings.hasNext()
        );
    }
}
