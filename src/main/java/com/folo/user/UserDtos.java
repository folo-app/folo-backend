package com.folo.user;

import com.folo.common.enums.PortfolioVisibility;
import com.folo.common.enums.ReturnVisibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

record MyProfileResponse(
        Long userId,
        String nickname,
        String profileImage,
        String bio,
        long followerCount,
        long followingCount,
        PortfolioVisibility portfolioVisibility,
        ReturnVisibility returnVisibility,
        String createdAt
) {
}

record UpdateMyProfileRequest(
        @Size(min = 2, max = 20) String nickname,
        @Size(max = 1000) String profileImage,
        @Size(max = 500) String bio,
        PortfolioVisibility portfolioVisibility,
        ReturnVisibility returnVisibility
) {
}

record UpdateKisKeyRequest(
        @NotBlank @Size(min = 8, max = 255) String kisAppKey,
        @NotBlank @Size(min = 8, max = 255) String kisAppSecret
) {
}

record PublicProfileResponse(
        Long userId,
        String nickname,
        String profileImage,
        String bio,
        long followerCount,
        long followingCount,
        boolean isFollowing,
        PortfolioVisibility portfolioVisibility,
        boolean isAccessible
) {
}

record UserSearchItem(
        Long userId,
        String nickname,
        String profileImage,
        long followerCount,
        boolean isFollowing
) {
}

record UserSearchResponse(
        List<UserSearchItem> users,
        long totalCount,
        boolean hasNext
) {
}
