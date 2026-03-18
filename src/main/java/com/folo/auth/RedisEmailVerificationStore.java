package com.folo.auth;

import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@Profile("!test")
public class RedisEmailVerificationStore implements EmailVerificationStore {

    private static final String CODE_PREFIX = "auth:email:code:";
    private static final String COOLDOWN_PREFIX = "auth:email:cooldown:";

    private final StringRedisTemplate stringRedisTemplate;

    public RedisEmailVerificationStore(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean isResendBlocked(String email) {
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(COOLDOWN_PREFIX + email));
    }

    @Override
    public void save(String email, String code, long ttlSeconds, long resendCooldownSeconds) {
        stringRedisTemplate.opsForValue().set(CODE_PREFIX + email, code, Duration.ofSeconds(ttlSeconds));
        stringRedisTemplate.opsForValue().set(COOLDOWN_PREFIX + email, "1", Duration.ofSeconds(resendCooldownSeconds));
    }

    @Override
    public boolean matches(String email, String code) {
        String stored = stringRedisTemplate.opsForValue().get(CODE_PREFIX + email);
        return code.equals(stored);
    }

    @Override
    public void clear(String email) {
        stringRedisTemplate.delete(CODE_PREFIX + email);
        stringRedisTemplate.delete(COOLDOWN_PREFIX + email);
    }
}
