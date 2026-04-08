package com.folo.auth;

import com.folo.common.enums.AuthProvider;

import java.util.Map;

public interface SocialAuthProviderClient {

    AuthProvider provider();

    SocialAuthStartResult start(SocialAuthStartCommand command);

    SocialProviderIdentity completeAuthorization(SocialAuthorizationState state, Map<String, String> callbackParams);
}
