package com.folo.notification;

import com.folo.common.enums.NotificationTargetType;
import com.folo.common.enums.NotificationType;
import com.folo.common.exception.ApiException;
import com.folo.common.exception.ErrorCode;
import com.folo.comment.Comment;
import com.folo.common.enums.ReactionEmoji;
import com.folo.trade.Trade;
import com.folo.user.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationSettingRepository notificationSettingRepository;

    public NotificationService(
            NotificationRepository notificationRepository,
            NotificationSettingRepository notificationSettingRepository
    ) {
        this.notificationRepository = notificationRepository;
        this.notificationSettingRepository = notificationSettingRepository;
    }

    @Transactional
    public void notifyFollow(User actor, User target) {
        NotificationSetting setting = notificationSettingRepository.findByUserId(target.getId())
                .orElseGet(() -> notificationSettingRepository.save(NotificationSetting.defaultOf(target)));
        if (!setting.isFollowAlert()) {
            return;
        }

        notificationRepository.save(new Notification(
                target,
                NotificationType.FOLLOW,
                actor,
                NotificationTargetType.USER,
                actor.getId(),
                actor.getNickname() + "님이 회원님을 팔로우했습니다."
        ));
    }

    @Transactional
    public void notifyReaction(User actor, Trade trade, ReactionEmoji emoji) {
        if (trade.getUser().getId().equals(actor.getId())) {
            return;
        }
        NotificationSetting setting = notificationSettingRepository.findByUserId(trade.getUser().getId())
                .orElseGet(() -> notificationSettingRepository.save(NotificationSetting.defaultOf(trade.getUser())));
        if (!setting.isReactionAlert()) {
            return;
        }

        notificationRepository.save(new Notification(
                trade.getUser(),
                NotificationType.REACTION,
                actor,
                NotificationTargetType.TRADE,
                trade.getId(),
                actor.getNickname() + "님이 회원님의 거래에 " + emoji.name() + " 반응을 남겼습니다."
        ));
    }

    @Transactional
    public void notifyComment(User actor, Trade trade, Comment comment) {
        if (trade.getUser().getId().equals(actor.getId())) {
            return;
        }
        NotificationSetting setting = notificationSettingRepository.findByUserId(trade.getUser().getId())
                .orElseGet(() -> notificationSettingRepository.save(NotificationSetting.defaultOf(trade.getUser())));
        if (!setting.isCommentAlert()) {
            return;
        }

        notificationRepository.save(new Notification(
                trade.getUser(),
                NotificationType.COMMENT,
                actor,
                NotificationTargetType.TRADE,
                trade.getId(),
                actor.getNickname() + "님이 회원님의 거래에 댓글을 남겼습니다."
        ));
    }

    @Transactional(readOnly = true)
    public NotificationListResponse list(Long userId, int page, int size) {
        Page<Notification> notifications = notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size));
        return new NotificationListResponse(
                notifications.getContent().stream()
                        .map(notification -> new NotificationItem(
                                notification.getId(),
                                notification.getType(),
                                notification.getMessage(),
                                notification.getTargetId(),
                                notification.isRead(),
                                notification.getCreatedAt().toString()
                        ))
                        .toList(),
                notificationRepository.countByUserIdAndIsReadFalse(userId),
                notifications.hasNext()
        );
    }

    @Transactional
    public void readAll(Long userId) {
        notificationRepository.findByUserId(userId)
                .forEach(notification -> notification.setRead(true));
    }

    @Transactional
    public NotificationReadResponse readOne(Long userId, Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "알림을 찾을 수 없습니다."));
        if (!notification.getUser().getId().equals(userId)) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }
        notification.setRead(true);
        return new NotificationReadResponse(notification.getId(), notification.isRead());
    }

    @Transactional
    public void updateSettings(Long userId, NotificationSettingsUpdateRequest request) {
        NotificationSetting setting = notificationSettingRepository.findByUserId(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "알림 설정을 찾을 수 없습니다."));
        if (request.reactionAlert() != null) {
            setting.setReactionAlert(request.reactionAlert());
        }
        if (request.commentAlert() != null) {
            setting.setCommentAlert(request.commentAlert());
        }
        if (request.followAlert() != null) {
            setting.setFollowAlert(request.followAlert());
        }
        if (request.reminderAlert() != null) {
            setting.setReminderAlert(request.reminderAlert());
        }
        if (request.nudgeAlert() != null) {
            setting.setNudgeAlert(request.nudgeAlert());
        }
    }
}
