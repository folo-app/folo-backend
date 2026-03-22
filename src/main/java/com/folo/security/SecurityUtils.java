package com.folo.security;

import com.folo.common.exception.ApiException;
import com.folo.common.exception.ErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.lang.Nullable;

public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static FoloUserPrincipal currentPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof FoloUserPrincipal principal)) {
            throw new ApiException(ErrorCode.UNAUTHORIZED);
        }
        return principal;
    }

    @Nullable
    public static FoloUserPrincipal currentPrincipalOrNull() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof FoloUserPrincipal principal)) {
            return null;
        }
        return principal;
    }

    public static Long currentUserId() {
        return currentPrincipal().userId();
    }

    @Nullable
    public static Long currentUserIdOrNull() {
        FoloUserPrincipal principal = currentPrincipalOrNull();
        return principal != null ? principal.userId() : null;
    }
}
