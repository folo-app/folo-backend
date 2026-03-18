package com.folo.notification;

record NotificationSettingsUpdateRequest(
        Boolean reactionAlert,
        Boolean commentAlert,
        Boolean followAlert,
        Boolean reminderAlert,
        Boolean nudgeAlert
) {
}
