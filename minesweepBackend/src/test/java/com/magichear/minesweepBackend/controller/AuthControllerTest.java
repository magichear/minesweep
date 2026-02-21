package com.magichear.minesweepBackend.controller;

import com.magichear.minesweepBackend.config.JwtUtil;
import com.magichear.minesweepBackend.entity.User;
import com.magichear.minesweepBackend.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private JwtUtil jwtUtil;

    // ---- login ----

    @Test
    void login_ok() throws Exception {
        User user = new User();
        user.setId(1L);
        user.setUsername("alice");

        when(userService.authenticate("alice", "pass123")).thenReturn(user);
        when(jwtUtil.generateToken("alice")).thenReturn("mock-jwt-token");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"pass123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("mock-jwt-token"))
                .andExpect(jsonPath("$.username").value("alice"));
    }

    @Test
    void login_invalidCredentials_returns400() throws Exception {
        when(userService.authenticate(anyString(), anyString()))
                .thenThrow(new IllegalArgumentException("Invalid username or password"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"wrong\"}"))
                .andExpect(status().isBadRequest());
    }

    // ---- register ----

    @Test
    void register_ok() throws Exception {
        User user = new User();
        user.setId(2L);
        user.setUsername("bob");

        when(userService.register("bob", "newpass")).thenReturn(user);
        when(jwtUtil.generateToken("bob")).thenReturn("new-jwt-token");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"bob\",\"password\":\"newpass\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("new-jwt-token"))
                .andExpect(jsonPath("$.username").value("bob"));
    }

    @Test
    void register_duplicateUsername_returns400() throws Exception {
        when(userService.register(eq("alice"), anyString()))
                .thenThrow(new IllegalArgumentException("Username already exists: alice"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"pass\"}"))
                .andExpect(status().isBadRequest());
    }
}
