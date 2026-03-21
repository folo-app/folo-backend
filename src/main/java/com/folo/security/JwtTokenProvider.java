package com.folo.security;

import com.folo.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtTokenProvider {

    private final JwtProperties jwtProperties;
    private final SecretKey secretKey;

    public JwtTokenProvider(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        byte[] rawBytes = jwtProperties.secret().getBytes(StandardCharsets.UTF_8);
        byte[] keyBytes = rawBytes.length >= 32 ? rawBytes : Arrays.copyOf(rawBytes, 32);
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateAccessToken(FoloUserPrincipal principal) {
        return generateToken(principal, jwtProperties.accessTokenExpirationSeconds(), "access");
    }

    public String generateRefreshToken(FoloUserPrincipal principal) {
        return generateToken(principal, jwtProperties.refreshTokenExpirationSeconds(), "refresh");
    }

    public Claims parse(String token) {
        return Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload();
    }

    public FoloUserPrincipal toPrincipal(String token) {
        Claims claims = parse(token);
        return new FoloUserPrincipal(
                claims.get("userId", Long.class),
                claims.getSubject()
        );
    }

    public FoloUserPrincipal toAccessPrincipal(String token) {
        Claims claims = parse(token);
        String type = claims.get("type", String.class);
        if (!"access".equals(type)) {
            throw new IllegalArgumentException("Access token is required");
        }

        return new FoloUserPrincipal(
                claims.get("userId", Long.class),
                claims.getSubject()
        );
    }

    public String getTokenType(String token) {
        return parse(token).get("type", String.class);
    }

    private String generateToken(FoloUserPrincipal principal, long expirationSeconds, String type) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(jwtProperties.issuer())
                .subject(principal.email())
                .claim("userId", principal.userId())
                .claim("type", type)
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expirationSeconds)))
                .signWith(secretKey)
                .compact();
    }
}
