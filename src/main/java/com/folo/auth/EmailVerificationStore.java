package com.folo.auth;

public interface EmailVerificationStore {

    boolean isResendBlocked(String email);

    void save(String email, String code, long ttlSeconds, long resendCooldownSeconds);

    boolean matches(String email, String code);

    void clear(String email);
}
