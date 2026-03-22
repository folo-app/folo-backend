package com.folo.user;

import com.folo.auth.UserAuthIdentity;
import com.folo.auth.UserAuthIdentityRepository;
import com.folo.common.exception.ApiException;
import com.folo.common.exception.ErrorCode;
import com.folo.follow.SocialRelationService;
import com.folo.security.FieldEncryptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.lang.Nullable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final UserAuthIdentityRepository userAuthIdentityRepository;
    private final SocialRelationService socialRelationService;
    private final FieldEncryptor fieldEncryptor;
    private final PasswordEncoder passwordEncoder;

    public UserService(
            UserRepository userRepository,
            UserAuthIdentityRepository userAuthIdentityRepository,
            SocialRelationService socialRelationService,
            FieldEncryptor fieldEncryptor,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.userAuthIdentityRepository = userAuthIdentityRepository;
        this.socialRelationService = socialRelationService;
        this.fieldEncryptor = fieldEncryptor;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public MyProfileResponse getMe(Long userId) {
        User user = userRepository.findByIdAndActiveTrue(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "사용자를 찾을 수 없습니다."));
        return new MyProfileResponse(
                user.getId(),
                user.getNickname(),
                user.getProfileImageUrl(),
                user.getBio(),
                socialRelationService.followerCount(userId),
                socialRelationService.followingCount(userId),
                user.getPortfolioVisibility(),
                user.getReturnVisibility(),
                user.getCreatedAt().toString()
        );
    }

    @Transactional
    public MyProfileResponse updateMe(Long userId, UpdateMyProfileRequest request) {
        User user = userRepository.findByIdAndActiveTrue(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "사용자를 찾을 수 없습니다."));

        if (request.nickname() != null && !request.nickname().equals(user.getNickname()) && userRepository.existsByNickname(request.nickname())) {
            throw new ApiException(ErrorCode.DUPLICATE_NICKNAME);
        }

        user.updateProfile(
                request.nickname(),
                request.profileImage(),
                request.bio(),
                request.portfolioVisibility(),
                request.returnVisibility()
        );

        return getMe(userId);
    }

    @Transactional
    public void updateKisKeys(Long userId, UpdateKisKeyRequest request) {
        User user = userRepository.findByIdAndActiveTrue(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "사용자를 찾을 수 없습니다."));
        if (request.kisAppKey() == null || request.kisAppSecret() == null) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "KIS 앱키와 시크릿을 모두 입력해주세요.");
        }
        user.setKisAppKeyEncrypted(fieldEncryptor.encrypt(request.kisAppKey()));
        user.setKisAppSecretEncrypted(fieldEncryptor.encrypt(request.kisAppSecret()));
    }

    @Transactional
    public void changeMyPassword(Long userId, ChangeMyPasswordRequest request) {
        UserAuthIdentity identity = userAuthIdentityRepository.findByUserIdAndProvider(userId, com.folo.common.enums.AuthProvider.EMAIL)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "이메일 계정을 찾을 수 없습니다."));

        if (!passwordEncoder.matches(request.currentPassword(), identity.getPasswordHash())) {
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS, "현재 비밀번호가 올바르지 않습니다.");
        }
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "새 비밀번호 확인이 일치하지 않습니다.");
        }
        if (request.currentPassword().equals(request.newPassword())) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "기존과 다른 새 비밀번호를 입력해 주세요.");
        }

        validatePassword(request.newPassword());
        identity.changePassword(passwordEncoder.encode(request.newPassword()));
    }

    @Transactional(readOnly = true)
    public PublicProfileResponse getProfile(Long currentUserId, Long targetUserId) {
        User target = userRepository.findByIdAndActiveTrue(targetUserId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "사용자를 찾을 수 없습니다."));

        boolean isFollowing = socialRelationService.isFollowing(currentUserId, targetUserId);
        boolean accessible = switch (target.getPortfolioVisibility()) {
            case PUBLIC -> true;
            case FRIENDS_ONLY -> socialRelationService.isMutualFollow(currentUserId, targetUserId);
            case PRIVATE -> currentUserId.equals(targetUserId);
        };

        return new PublicProfileResponse(
                target.getId(),
                target.getNickname(),
                target.getProfileImageUrl(),
                target.getBio(),
                socialRelationService.followerCount(targetUserId),
                socialRelationService.followingCount(targetUserId),
                isFollowing,
                target.getPortfolioVisibility(),
                accessible
        );
    }

    @Transactional(readOnly = true)
    public UserSearchResponse search(Long currentUserId, @Nullable String q, int page, int size) {
        if (q == null || q.trim().length() < 2) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "검색어는 2자 이상이어야 합니다.");
        }

        Page<User> result = userRepository.findByNicknameContainingIgnoreCaseAndActiveTrue(q.trim(), PageRequest.of(page, size));
        return new UserSearchResponse(
                result.getContent().stream()
                        .map(user -> new UserSearchItem(
                                user.getId(),
                                user.getNickname(),
                                user.getProfileImageUrl(),
                                socialRelationService.followerCount(user.getId()),
                                socialRelationService.isFollowing(currentUserId, user.getId())
                        ))
                        .toList(),
                result.getTotalElements(),
                result.hasNext()
        );
    }

    private void validatePassword(String password) {
        boolean valid = password.matches("^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z\\d]).{8,100}$");
        if (!valid) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "비밀번호는 8자 이상이며 영문, 숫자, 특수문자를 포함해야 합니다.");
        }
    }
}
