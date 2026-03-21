package com.folo.auth;

import com.folo.common.entity.BaseTimeEntity;
import com.folo.common.enums.AuthProvider;
import com.folo.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "user_auth_identities",
        uniqueConstraints = @UniqueConstraint(name = "uk_auth_provider_user", columnNames = {"provider", "provider_user_id"}),
        indexes = {
                @Index(name = "idx_auth_user_id", columnList = "user_id"),
                @Index(name = "idx_auth_email", columnList = "email")
        })
public class UserAuthIdentity extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuthProvider provider;

    @Column(nullable = false, length = 255)
    private String providerUserId;

    @Column(length = 255)
    private String email;

    @Column(length = 255)
    private String passwordHash;

    @Column(nullable = false)
    private boolean emailVerified;

    public UserAuthIdentity(User user, String email, String passwordHash) {
        this.user = user;
        this.provider = AuthProvider.EMAIL;
        this.providerUserId = email;
        this.email = email;
        this.passwordHash = passwordHash;
        this.emailVerified = false;
    }

    public void verifyEmail() {
        this.emailVerified = true;
    }

    public void changePassword(String passwordHash) {
        this.passwordHash = passwordHash;
    }
}
