package com.folo.security;

import java.io.Serializable;

public record FoloUserPrincipal(
        Long userId,
        String email
) implements Serializable {
}
