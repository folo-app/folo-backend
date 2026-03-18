package com.folo.user;

import com.folo.common.api.ApiResponse;
import com.folo.security.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public ApiResponse<MyProfileResponse> getMe() {
        return ApiResponse.success(userService.getMe(SecurityUtils.currentUserId()), "요청이 성공했습니다.");
    }

    @PatchMapping("/me")
    public ApiResponse<MyProfileResponse> updateMe(@Valid @RequestBody UpdateMyProfileRequest request) {
        return ApiResponse.success(userService.updateMe(SecurityUtils.currentUserId(), request), "프로필이 수정되었습니다.");
    }

    @GetMapping("/{userId}")
    public ApiResponse<PublicProfileResponse> getUser(@PathVariable Long userId) {
        return ApiResponse.success(userService.getProfile(SecurityUtils.currentUserId(), userId), "요청이 성공했습니다.");
    }

    @GetMapping("/search")
    public ApiResponse<UserSearchResponse> search(
            @RequestParam String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.success(userService.search(SecurityUtils.currentUserId(), q, page, size), "요청이 성공했습니다.");
    }

    @PatchMapping("/me/kis-key")
    public ApiResponse<Void> updateKisKey(@Valid @RequestBody UpdateKisKeyRequest request) {
        userService.updateKisKeys(SecurityUtils.currentUserId(), request);
        return ApiResponse.successMessage("한국투자증권 앱키가 등록되었습니다.");
    }
}
