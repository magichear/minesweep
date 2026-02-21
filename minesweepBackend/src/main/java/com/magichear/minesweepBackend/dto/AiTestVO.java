package com.magichear.minesweepBackend.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AiTestVO {
    private Long id;
    private String testName;
    private String modelName;
    private String difficulty;
    private int totalGames;
    private int wins;
    private double winRate;
    private Long avgDurationMs;
    private Long maxDurationMs;
    private Long minDurationMs;
    private String username;
    private LocalDateTime createdAt;
}
