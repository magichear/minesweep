package com.magichear.minesweepBackend.config;

import com.magichear.minesweepBackend.config.properties.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import javax.crypto.SecretKey;

/**
 * JWT token utility based on io.jsonwebtoken (JJWT).
 */
@Slf4j
@Component
public class JwtUtil {

    private final SecretKey key;
    private final long expirationMs;

    public JwtUtil(JwtProperties jwtProperties) {
        String secret = jwtProperties.getSecret();
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = jwtProperties.getExpirationMs();
    }

    public String generateToken(String username) {
        try {
            Date now = new Date();
            Date expiration = new Date(now.getTime() + expirationMs);

            return Jwts.builder()
                    .subject(username)
                    .issuedAt(now)
                    .expiration(expiration)
                    .signWith(key)
                    .compact();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate JWT token", e);
        }
    }

    public String validateAndGetUsername(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return claims.getSubject();
        } catch (JwtException | IllegalArgumentException e) {
            throw new SecurityException("Invalid token: " + e.getMessage());
        }
    }
}
