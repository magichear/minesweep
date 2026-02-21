package com.magichear.minesweepBackend.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private DefaultUser defaultUser = new DefaultUser();

    @Data
    public static class DefaultUser {
        private boolean enabled;
        private String username;
        private String password;
    }
}
