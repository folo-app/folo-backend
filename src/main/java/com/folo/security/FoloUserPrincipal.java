package com.folo.security;

import org.springframework.lang.Nullable;

import java.io.Serializable;

public record FoloUserPrincipal(
        Long userId,
        @Nullable String email
) implements Serializable {
}
