package com.folo.auth;

import com.folo.common.entity.BaseTimeEntity;
import com.folo.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.lang.Nullable;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "refresh_tokens", indexes = {
        @Index(name = "idx_refresh_user_id", columnList = "user_id"),
        @Index(name = "idx_refresh_expires_at", columnList = "expires_at"),
        @Index(name = "idx_refresh_auth_identity_id", columnList = "auth_identity_id")
})
public class RefreshToken extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "auth_identity_id")
    @Nullable
    private UserAuthIdentity authIdentity;

    @Column(nullable = false, unique = true, length = 500)
    private String tokenHash;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    private LocalDateTime revokedAt;

    @Column(length = 255)
    private String deviceId;

    @Column(length = 255)
    private String deviceName;

    public RefreshToken(User user, String tokenHash, LocalDateTime expiresAt, String deviceId, String deviceName) {
        this(user, null, tokenHash, expiresAt, deviceId, deviceName);
    }

    public RefreshToken(
            User user,
            @Nullable UserAuthIdentity authIdentity,
            String tokenHash,
            LocalDateTime expiresAt,
            @Nullable String deviceId,
            @Nullable String deviceName
    ) {
        this.user = user;
        this.authIdentity = authIdentity;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.deviceId = deviceId;
        this.deviceName = deviceName;
    }

    public boolean isExpired(LocalDateTime now) {
        return expiresAt.isBefore(now);
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public void revoke() {
        this.revokedAt = LocalDateTime.now();
    }
}
