package com.folo.notification;

import org.springframework.lang.Nullable;

record NotificationSettingsUpdateRequest(
        @Nullable Boolean reactionAlert,
        @Nullable Boolean commentAlert,
        @Nullable Boolean followAlert,
        @Nullable Boolean reminderAlert,
        @Nullable Boolean nudgeAlert
) {
}
