package com.magichear.minesweepBackend.config;

import com.magichear.minesweepBackend.config.properties.JwtProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtUtilTest {

    private static final String SECRET = "minesweep-jwt-secret-key-2026-change-in-production";

    @Test
    void generateAndValidateToken_ok() {
        JwtUtil jwtUtil = new JwtUtil(buildProps(60_000));

        String token = jwtUtil.generateToken("alice");
        String username = jwtUtil.validateAndGetUsername(token);

        assertEquals("alice", username);
    }

    @Test
    void validateToken_expired_throwsSecurityException() {
        JwtUtil jwtUtil = new JwtUtil(buildProps(-1_000));

        String token = jwtUtil.generateToken("alice");

        assertThrows(SecurityException.class, () -> jwtUtil.validateAndGetUsername(token));
    }

    @Test
    void validateToken_tamperedSignature_throwsSecurityException() {
        JwtUtil jwtUtil = new JwtUtil(buildProps(60_000));

        String token = jwtUtil.generateToken("alice");
        String tamperedToken = token.substring(0, token.length() - 1)
                + (token.endsWith("a") ? "b" : "a");

        assertThrows(SecurityException.class, () -> jwtUtil.validateAndGetUsername(tamperedToken));
    }

    @Test
    void validateToken_invalidFormat_throwsSecurityException() {
        JwtUtil jwtUtil = new JwtUtil(buildProps(60_000));

        assertThrows(SecurityException.class, () -> jwtUtil.validateAndGetUsername("not-a-jwt"));
    }

    private JwtProperties buildProps(long expirationMs) {
        JwtProperties properties = new JwtProperties();
        properties.setSecret(SECRET);
        properties.setExpirationMs(expirationMs);
        return properties;
    }
}
