package com.folo.notification;

import com.folo.common.enums.NotificationType;

import java.util.List;

record NotificationItem(
        Long notificationId,
        NotificationType type,
        String message,
        Long targetId,
        boolean isRead,
        String createdAt
) {
}

record NotificationListResponse(
        List<NotificationItem> notifications,
        long unreadCount,
        boolean hasNext
) {
}

record NotificationReadResponse(
        Long notificationId,
        boolean isRead
) {
}
