package com.magichear.minesweepBackend.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "app_user")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private String salt;

    @Column(nullable = false)
    private LocalDateTime createdAt;
}
