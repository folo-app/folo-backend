package com.folo.reminder;

import com.folo.common.api.ApiResponse;
import com.folo.security.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/reminders")
public class ReminderController {

    private final ReminderService reminderService;

    public ReminderController(ReminderService reminderService) {
        this.reminderService = reminderService;
    }

    @GetMapping
    public ApiResponse<ReminderListResponse> list() {
        return ApiResponse.success(reminderService.list(SecurityUtils.currentUserId()), "요청이 성공했습니다.");
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ReminderItem> create(@Valid @RequestBody CreateReminderRequest request) {
        return ApiResponse.success(reminderService.create(SecurityUtils.currentUserId(), request), "리마인더가 생성되었습니다.");
    }

    @PatchMapping("/{reminderId}")
    public ApiResponse<ReminderItem> update(@PathVariable Long reminderId, @Valid @RequestBody UpdateReminderRequest request) {
        return ApiResponse.success(reminderService.update(SecurityUtils.currentUserId(), reminderId, request), "리마인더가 수정되었습니다.");
    }

    @DeleteMapping("/{reminderId}")
    public ApiResponse<Void> delete(@PathVariable Long reminderId) {
        reminderService.delete(SecurityUtils.currentUserId(), reminderId);
        return ApiResponse.successMessage("알림이 삭제되었습니다.");
    }
}
