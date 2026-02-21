package com.magichear.minesweepBackend.entity;

import com.magichear.minesweepBackend.model.Difficulty;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "ai_test_record")
public class AiTestRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String testName;

    @Column(nullable = false, length = 100)
    private String modelName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Difficulty difficulty;

    @Column(nullable = false)
    private int totalGames;

    @Column(nullable = false)
    private int wins;

    @Column(nullable = false)
    private double winRate;

    private Long avgDurationMs;

    private Long maxDurationMs;

    private Long minDurationMs;

    @Column(nullable = false, length = 50)
    private String username;

    @Column(nullable = false)
    private LocalDateTime createdAt;
}
