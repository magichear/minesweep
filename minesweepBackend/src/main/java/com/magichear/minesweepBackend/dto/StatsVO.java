package com.magichear.minesweepBackend.dto;

import lombok.Data;

@Data
public class StatsVO {
    private DifficultyStatsVO easy;
    private DifficultyStatsVO medium;
    private DifficultyStatsVO hard;
    private double globalWinRate;
    private int globalTotalGames;
}
