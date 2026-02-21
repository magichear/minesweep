package com.magichear.minesweepBackend.config;

import com.magichear.minesweepBackend.config.properties.AppProperties;
import com.magichear.minesweepBackend.repository.UserRepository;
import com.magichear.minesweepBackend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Initialises default data on first run.
 * Creates default user from configuration if enabled.
 */
@Slf4j
@Component
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final UserService userService;
    private final AppProperties appProperties;

    public DataInitializer(UserRepository userRepository, UserService userService,
                           AppProperties appProperties) {
        this.userRepository = userRepository;
        this.userService = userService;
        this.appProperties = appProperties;
    }

    @Override
    public void run(String... args) {
        AppProperties.DefaultUser defaultUser = appProperties.getDefaultUser();
        if (defaultUser == null) {
            log.warn("Default user config is missing, skipping initialization");
            return;
        }

        boolean defaultUserEnabled = defaultUser.isEnabled();
        String defaultUsername = defaultUser.getUsername();
        String defaultPassword = defaultUser.getPassword();

        if (!defaultUserEnabled) {
            log.info("Default user initialization disabled by config");
            return;
        }
        if (defaultUsername == null || defaultUsername.isBlank()
                || defaultPassword == null || defaultPassword.isBlank()) {
            log.warn("Default user config is incomplete, skipping initialization");
            return;
        }

        if (!userRepository.existsByUsername(defaultUsername)) {
            userService.register(defaultUsername, defaultPassword);
            log.info("Default user '{}' created", defaultUsername);
        } else {
            log.info("Default user '{}' already exists", defaultUsername);
        }
    }
}
