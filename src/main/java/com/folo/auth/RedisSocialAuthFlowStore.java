package com.folo.auth;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@Profile("!test")
public class RedisSocialAuthFlowStore implements SocialAuthFlowStore {

    private static final String STATE_PREFIX = "auth:social:state:";
    private static final String HANDOFF_PREFIX = "auth:social:handoff:";
    private static final String SIGNUP_PREFIX = "auth:social:pending-signup:";
    private static final String LINK_PREFIX = "auth:social:pending-link:";

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    public RedisSocialAuthFlowStore(StringRedisTemplate stringRedisTemplate, ObjectMapper objectMapper) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void saveAuthorizationState(String state, SocialAuthorizationState payload, long ttlSeconds) {
        saveJson(STATE_PREFIX + state, payload, ttlSeconds);
    }

    @Override
    public @Nullable SocialAuthorizationState consumeAuthorizationState(String state) {
        return consumeJson(STATE_PREFIX + state, SocialAuthorizationState.class);
    }

    @Override
    public void saveHandoffSession(String handoffCode, SocialHandoffSession payload, long ttlSeconds) {
        saveJson(HANDOFF_PREFIX + handoffCode, payload, ttlSeconds);
    }

    @Override
    public @Nullable SocialHandoffSession consumeHandoffSession(String handoffCode) {
        return consumeJson(HANDOFF_PREFIX + handoffCode, SocialHandoffSession.class);
    }

    @Override
    public void savePendingSignup(String pendingToken, PendingSocialSignup payload, long ttlSeconds) {
        saveJson(SIGNUP_PREFIX + pendingToken, payload, ttlSeconds);
    }

    @Override
    public @Nullable PendingSocialSignup consumePendingSignup(String pendingToken) {
        return consumeJson(SIGNUP_PREFIX + pendingToken, PendingSocialSignup.class);
    }

    @Override
    public void savePendingLink(String pendingToken, PendingSocialLink payload, long ttlSeconds) {
        saveJson(LINK_PREFIX + pendingToken, payload, ttlSeconds);
    }

    @Override
    public @Nullable PendingSocialLink consumePendingLink(String pendingToken) {
        return consumeJson(LINK_PREFIX + pendingToken, PendingSocialLink.class);
    }

    private void saveJson(String key, Object payload, long ttlSeconds) {
        try {
            stringRedisTemplate.opsForValue().set(
                    key,
                    objectMapper.writeValueAsString(payload),
                    Duration.ofSeconds(ttlSeconds)
            );
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("소셜 로그인 상태 저장 직렬화에 실패했습니다.", exception);
        }
    }

    private <T> @Nullable T consumeJson(String key, Class<T> type) {
        String stored = stringRedisTemplate.opsForValue().get(key);
        stringRedisTemplate.delete(key);
        if (stored == null) {
            return null;
        }

        try {
            return objectMapper.readValue(stored, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("소셜 로그인 상태 역직렬화에 실패했습니다.", exception);
        }
    }
}
