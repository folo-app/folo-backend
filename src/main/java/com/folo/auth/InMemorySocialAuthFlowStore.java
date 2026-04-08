package com.folo.auth;

import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Profile("test")
public class InMemorySocialAuthFlowStore implements SocialAuthFlowStore {

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    @Override
    public void saveAuthorizationState(String state, SocialAuthorizationState payload, long ttlSeconds) {
        save("state:" + state, payload, ttlSeconds);
    }

    @Override
    public @Nullable SocialAuthorizationState consumeAuthorizationState(String state) {
        return consume("state:" + state, SocialAuthorizationState.class);
    }

    @Override
    public void saveHandoffSession(String handoffCode, SocialHandoffSession payload, long ttlSeconds) {
        save("handoff:" + handoffCode, payload, ttlSeconds);
    }

    @Override
    public @Nullable SocialHandoffSession consumeHandoffSession(String handoffCode) {
        return consume("handoff:" + handoffCode, SocialHandoffSession.class);
    }

    @Override
    public void savePendingSignup(String pendingToken, PendingSocialSignup payload, long ttlSeconds) {
        save("signup:" + pendingToken, payload, ttlSeconds);
    }

    @Override
    public @Nullable PendingSocialSignup consumePendingSignup(String pendingToken) {
        return consume("signup:" + pendingToken, PendingSocialSignup.class);
    }

    @Override
    public void savePendingLink(String pendingToken, PendingSocialLink payload, long ttlSeconds) {
        save("link:" + pendingToken, payload, ttlSeconds);
    }

    @Override
    public @Nullable PendingSocialLink consumePendingLink(String pendingToken) {
        return consume("link:" + pendingToken, PendingSocialLink.class);
    }

    private void save(String key, Object payload, long ttlSeconds) {
        entries.put(key, new Entry(payload, Instant.now().plusSeconds(ttlSeconds)));
    }

    private <T> @Nullable T consume(String key, Class<T> type) {
        Entry entry = entries.remove(key);
        if (entry == null || entry.expiresAt().isBefore(Instant.now())) {
            return null;
        }
        return type.cast(entry.payload());
    }

    private record Entry(Object payload, Instant expiresAt) {
    }
}
