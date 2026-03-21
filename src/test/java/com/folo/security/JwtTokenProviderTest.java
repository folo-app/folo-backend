package com.folo.security;

import com.folo.config.JwtProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtTokenProviderTest {

    private final JwtTokenProvider jwtTokenProvider = new JwtTokenProvider(new JwtProperties(
            "test-issuer",
            900,
            1209600,
            "test-secret-test-secret-test-secret-test-secret"
    ));

    @Test
    void accessTokenCanBeUsedAsAuthenticatedPrincipal() {
        FoloUserPrincipal principal = new FoloUserPrincipal(1L, "alpha@example.com");

        String accessToken = jwtTokenProvider.generateAccessToken(principal);

        assertEquals(principal, jwtTokenProvider.toAccessPrincipal(accessToken));
        assertEquals("access", jwtTokenProvider.getTokenType(accessToken));
    }

    @Test
    void refreshTokenCannotBeUsedAsAuthenticatedPrincipal() {
        FoloUserPrincipal principal = new FoloUserPrincipal(1L, "alpha@example.com");

        String refreshToken = jwtTokenProvider.generateRefreshToken(principal);

        assertEquals("refresh", jwtTokenProvider.getTokenType(refreshToken));
        assertThrows(IllegalArgumentException.class, () -> jwtTokenProvider.toAccessPrincipal(refreshToken));
    }
}
