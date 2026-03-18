package com.folo.notification;

import com.folo.common.entity.BaseTimeEntity;
import com.folo.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "notification_settings")
public class NotificationSetting extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false)
    private boolean reactionAlert;

    @Column(nullable = false)
    private boolean commentAlert;

    @Column(nullable = false)
    private boolean followAlert;

    @Column(nullable = false)
    private boolean reminderAlert;

    @Column(nullable = false)
    private boolean nudgeAlert;

    public static NotificationSetting defaultOf(User user) {
        NotificationSetting setting = new NotificationSetting();
        setting.user = user;
        setting.reactionAlert = true;
        setting.commentAlert = true;
        setting.followAlert = true;
        setting.reminderAlert = true;
        setting.nudgeAlert = true;
        return setting;
    }
}
