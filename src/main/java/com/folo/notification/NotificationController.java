package com.folo.notification;

import com.folo.common.api.ApiResponse;
import com.folo.security.SecurityUtils;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public ApiResponse<NotificationListResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.success(notificationService.list(SecurityUtils.currentUserId(), page, size), "요청이 성공했습니다.");
    }

    @PatchMapping("/read")
    public ApiResponse<Void> readAll() {
        notificationService.readAll(SecurityUtils.currentUserId());
        return ApiResponse.successMessage("모든 알림을 읽음 처리했습니다.");
    }

    @PatchMapping("/{notificationId}/read")
    public ApiResponse<NotificationReadResponse> readOne(@PathVariable Long notificationId) {
        return ApiResponse.success(notificationService.readOne(SecurityUtils.currentUserId(), notificationId), "요청이 성공했습니다.");
    }

    @PatchMapping("/settings")
    public ApiResponse<Void> updateSettings(@RequestBody NotificationSettingsUpdateRequest request) {
        notificationService.updateSettings(SecurityUtils.currentUserId(), request);
        return ApiResponse.successMessage("알림 설정이 저장되었습니다.");
    }
}
