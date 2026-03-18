package com.folo.user;

import com.folo.common.entity.BaseTimeEntity;
import com.folo.common.enums.PortfolioVisibility;
import com.folo.common.enums.ReturnVisibility;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "users", indexes = {
        @Index(name = "idx_user_handle", columnList = "handle", unique = true),
        @Index(name = "idx_user_nickname", columnList = "nickname", unique = true)
})
public class User extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String handle;

    @Column(nullable = false, unique = true, length = 20)
    private String nickname;

    @Column(length = 500)
    private String bio;

    @Column(length = 1000)
    private String profileImageUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PortfolioVisibility portfolioVisibility;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReturnVisibility returnVisibility;

    @Column(nullable = false)
    private boolean active;

    private LocalDateTime withdrawnAt;

    @Column(length = 255)
    private String kisAppKeyEncrypted;

    @Column(length = 255)
    private String kisAppSecretEncrypted;

    public User(String handle, String nickname, String profileImageUrl) {
        this.handle = handle;
        this.nickname = nickname;
        this.profileImageUrl = profileImageUrl;
        this.portfolioVisibility = PortfolioVisibility.FRIENDS_ONLY;
        this.returnVisibility = ReturnVisibility.RATE_ONLY;
        this.active = true;
    }

    public void updateProfile(String nickname, String profileImageUrl, String bio,
                              PortfolioVisibility portfolioVisibility, ReturnVisibility returnVisibility) {
        if (nickname != null) {
            this.nickname = nickname;
        }
        if (profileImageUrl != null) {
            this.profileImageUrl = profileImageUrl;
        }
        if (bio != null) {
            this.bio = bio;
        }
        if (portfolioVisibility != null) {
            this.portfolioVisibility = portfolioVisibility;
        }
        if (returnVisibility != null) {
            this.returnVisibility = returnVisibility;
        }
    }

    public void withdraw() {
        this.active = false;
        this.withdrawnAt = LocalDateTime.now();
    }
}
