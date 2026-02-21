package com.magichear.minesweepBackend.service;

import com.magichear.minesweepBackend.entity.User;
import com.magichear.minesweepBackend.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

/**
 * User registration and authentication with SHA-256 + random salt password hashing.
 */
@Slf4j
@Service
public class UserService {

    private final UserRepository userRepository;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User register(String username, String password) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username must not be empty");
        }
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("Password must not be empty");
        }
        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("Username already exists: " + username);
        }

        String salt = generateSalt();
        String hash = hashPassword(password, salt);

        User user = new User();
        user.setUsername(username);
        user.setSalt(salt);
        user.setPasswordHash(hash);
        user.setCreatedAt(LocalDateTime.now());

        user = userRepository.save(user);
        log.info("User registered: {}", username);
        return user;
    }

    public User authenticate(String username, String password) {
        Optional<User> userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty()) {
            throw new IllegalArgumentException("Invalid username or password");
        }
        User user = userOpt.get();
        String hash = hashPassword(password, user.getSalt());
        if (!hash.equals(user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid username or password");
        }
        return user;
    }

    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    // ---- Password hashing with salt (SHA-256 × 1000 rounds) ----

    static String generateSalt() {
        byte[] saltBytes = new byte[16];
        SECURE_RANDOM.nextBytes(saltBytes);
        return Base64.getEncoder().encodeToString(saltBytes);
    }

    static String hashPassword(String password, String salt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((salt + password).getBytes(StandardCharsets.UTF_8));
            for (int i = 0; i < 1000; i++) {
                hash = digest.digest(hash);
            }
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
