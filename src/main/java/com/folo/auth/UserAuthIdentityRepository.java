package com.folo.auth;

import com.folo.common.enums.AuthProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserAuthIdentityRepository extends JpaRepository<UserAuthIdentity, Long> {

    boolean existsByEmail(String email);

    Optional<UserAuthIdentity> findByProviderAndProviderUserId(AuthProvider provider, String providerUserId);

    Optional<UserAuthIdentity> findByEmail(String email);

    Optional<UserAuthIdentity> findByUserIdAndProvider(Long userId, AuthProvider provider);
}
