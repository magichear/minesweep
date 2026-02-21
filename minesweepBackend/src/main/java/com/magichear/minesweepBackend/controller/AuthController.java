package com.magichear.minesweepBackend.controller;

import com.magichear.minesweepBackend.config.JwtUtil;
import com.magichear.minesweepBackend.dto.AuthResponse;
import com.magichear.minesweepBackend.dto.LoginRequest;
import com.magichear.minesweepBackend.dto.RegisterRequest;
import com.magichear.minesweepBackend.entity.User;
import com.magichear.minesweepBackend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;
    private final JwtUtil jwtUtil;

    public AuthController(UserService userService, JwtUtil jwtUtil) {
        this.userService = userService;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {
        log.info("POST /api/auth/login – user={}", request.getUsername());
        User user = userService.authenticate(request.getUsername(), request.getPassword());
        String token = jwtUtil.generateToken(user.getUsername());
        return ResponseEntity.ok(new AuthResponse(token, user.getUsername()));
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@RequestBody RegisterRequest request) {
        log.info("POST /api/auth/register – user={}", request.getUsername());
        User user = userService.register(request.getUsername(), request.getPassword());
        String token = jwtUtil.generateToken(user.getUsername());
        return ResponseEntity.ok(new AuthResponse(token, user.getUsername()));
    }
}
