package com.magichear.minesweepBackend.service;

import com.magichear.minesweepBackend.entity.User;
import com.magichear.minesweepBackend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository);
    }

    // ---- register ----

    @Test
    void register_success() {
        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(1L);
            return u;
        });

        User user = userService.register("alice", "pass123");

        assertEquals("alice", user.getUsername());
        assertNotNull(user.getSalt());
        assertNotNull(user.getPasswordHash());
        assertNotNull(user.getCreatedAt());
        assertNotEquals("pass123", user.getPasswordHash(), "Password must not be stored in plain text");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertEquals("alice", captor.getValue().getUsername());
    }

    @Test
    void register_duplicateUsername_throws() {
        when(userRepository.existsByUsername("alice")).thenReturn(true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> userService.register("alice", "pass123"));
        assertTrue(ex.getMessage().contains("already exists"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void register_emptyUsername_throws() {
        assertThrows(IllegalArgumentException.class, () -> userService.register("", "pass123"));
        assertThrows(IllegalArgumentException.class, () -> userService.register("  ", "pass123"));
        assertThrows(IllegalArgumentException.class, () -> userService.register(null, "pass123"));
    }

    @Test
    void register_emptyPassword_throws() {
        assertThrows(IllegalArgumentException.class, () -> userService.register("alice", ""));
        assertThrows(IllegalArgumentException.class, () -> userService.register("alice", null));
    }

    // ---- authenticate ----

    @Test
    void authenticate_success() {
        // Prepare a user with known salt and hash
        String salt = UserService.generateSalt();
        String hash = UserService.hashPassword("secret", salt);

        User stored = new User();
        stored.setId(1L);
        stored.setUsername("bob");
        stored.setSalt(salt);
        stored.setPasswordHash(hash);

        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(stored));

        User result = userService.authenticate("bob", "secret");
        assertEquals("bob", result.getUsername());
    }

    @Test
    void authenticate_wrongPassword_throws() {
        String salt = UserService.generateSalt();
        String hash = UserService.hashPassword("correct", salt);

        User stored = new User();
        stored.setUsername("bob");
        stored.setSalt(salt);
        stored.setPasswordHash(hash);

        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(stored));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> userService.authenticate("bob", "wrong"));
        assertTrue(ex.getMessage().contains("Invalid"));
    }

    @Test
    void authenticate_unknownUser_throws() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> userService.authenticate("ghost", "pass"));
        assertTrue(ex.getMessage().contains("Invalid"));
    }

    // ---- hash / salt utilities ----

    @Test
    void hashPassword_deterministic() {
        String salt = "fixedSalt";
        String h1 = UserService.hashPassword("pw", salt);
        String h2 = UserService.hashPassword("pw", salt);
        assertEquals(h1, h2, "Hashing the same password+salt must produce the same result");
    }

    @Test
    void hashPassword_differentSalt_differentHash() {
        String h1 = UserService.hashPassword("pw", "salt1");
        String h2 = UserService.hashPassword("pw", "salt2");
        assertNotEquals(h1, h2, "Different salts must produce different hashes");
    }

    @Test
    void generateSalt_unique() {
        String s1 = UserService.generateSalt();
        String s2 = UserService.generateSalt();
        assertNotNull(s1);
        assertNotNull(s2);
        assertNotEquals(s1, s2, "Consecutive salts should be unique");
    }
}
