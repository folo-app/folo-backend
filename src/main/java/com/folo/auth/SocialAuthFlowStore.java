package com.folo.auth;

import org.springframework.lang.Nullable;

public interface SocialAuthFlowStore {

    void saveAuthorizationState(String state, SocialAuthorizationState payload, long ttlSeconds);

    @Nullable
    SocialAuthorizationState consumeAuthorizationState(String state);

    void saveHandoffSession(String handoffCode, SocialHandoffSession payload, long ttlSeconds);

    @Nullable
    SocialHandoffSession consumeHandoffSession(String handoffCode);

    void savePendingSignup(String pendingToken, PendingSocialSignup payload, long ttlSeconds);

    @Nullable
    PendingSocialSignup consumePendingSignup(String pendingToken);

    void savePendingLink(String pendingToken, PendingSocialLink payload, long ttlSeconds);

    @Nullable
    PendingSocialLink consumePendingLink(String pendingToken);
}
