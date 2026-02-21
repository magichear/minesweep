package com.magichear.minesweepBackend.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the JWT authentication filter for /api/* endpoints.
 */
@Configuration
public class SecurityFilterConfig {

    @Bean
    public FilterRegistrationBean<JwtAuthFilter> jwtAuthFilterRegistration(JwtUtil jwtUtil) {
        FilterRegistrationBean<JwtAuthFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new JwtAuthFilter(jwtUtil));
        registration.addUrlPatterns("/api/*");
        registration.setOrder(1);
        return registration;
    }
}
