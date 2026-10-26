package com.tmapi.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.UUID;
import java.util.function.Function;

/**
 * Stateless JWT token provider.
 * Tokens carry userId (sub), email, and are signed with HMAC-SHA256.
 * No server-side session state required — each request self-validates.
 */
@Component
@Slf4j
public class JwtTokenProvider {

    private final SecretKey secretKey;
    private final long expirationMs;
    private final long refreshExpirationMs;

    public JwtTokenProvider(
        @Value("${app.jwt.secret}") String secret,
        @Value("${app.jwt.expiration-ms}") long expirationMs,
        @Value("${app.jwt.refresh-expiration-ms}") long refreshExpirationMs
    ) {
        byte[] keyBytes = Decoders.BASE64.decode(secret);
        this.secretKey          = Keys.hmacShaKeyFor(keyBytes);
        this.expirationMs       = expirationMs;
        this.refreshExpirationMs = refreshExpirationMs;
    }

    /** Generates a short-lived access token */
    public String generateAccessToken(TmApiUserDetails userDetails) {
        return buildToken(userDetails.getUserId().toString(), userDetails.getEmail(), expirationMs);
    }

    /** Generates a longer-lived refresh token */
    public String generateRefreshToken(TmApiUserDetails userDetails) {
        return buildToken(userDetails.getUserId().toString(), userDetails.getEmail(), refreshExpirationMs);
    }

    private String buildToken(String userId, String email, long ttlMs) {
        return Jwts.builder()
            .subject(userId)
            .claim("email", email)
            .id(UUID.randomUUID().toString())
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + ttlMs))
            .signWith(secretKey)
            .compact();
    }

    public UUID extractUserId(String token) {
        return UUID.fromString(extractClaim(token, Claims::getSubject));
    }

    public String extractEmail(String token) {
        return extractClaim(token, claims -> claims.get("email", String.class));
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        try {
            final String email = extractEmail(token);
            return email.equals(userDetails.getUsername()) && !isTokenExpired(token);
        } catch (JwtException ex) {
            log.warn("JWT validation failed: {}", ex.getMessage());
            return false;
        }
    }

    public boolean isTokenExpired(String token) {
        return extractClaim(token, Claims::getExpiration).before(new Date());
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        Claims claims = Jwts.parser()
            .verifyWith(secretKey)
            .build()
            .parseSignedClaims(token)
            .getPayload();
        return claimsResolver.apply(claims);
    }
}
