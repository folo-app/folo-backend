package com.folo.auth;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.lang.Nullable;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Profile("test")
public class InMemoryEmailVerificationStore implements EmailVerificationStore {

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    @Override
    public boolean isResendBlocked(String email) {
        Entry entry = entries.get(email);
        return entry != null && entry.cooldownUntil().isAfter(Instant.now());
    }

    @Override
    public void save(String email, String code, long ttlSeconds, long resendCooldownSeconds) {
        entries.put(email, new Entry(
                code,
                Instant.now().plusSeconds(ttlSeconds),
                Instant.now().plusSeconds(resendCooldownSeconds)
        ));
    }

    @Override
    public boolean matches(String email, String code) {
        Entry entry = entries.get(email);
        return entry != null && entry.expiresAt().isAfter(Instant.now()) && entry.code().equals(code);
    }

    @Override
    public void clear(String email) {
        entries.remove(email);
    }

    public @Nullable String getCode(String email) {
        Entry entry = entries.get(email);
        return entry != null ? entry.code() : null;
    }

    private record Entry(String code, Instant expiresAt, Instant cooldownUntil) {
    }
}
