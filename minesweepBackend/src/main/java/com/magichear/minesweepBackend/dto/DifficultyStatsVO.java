package com.magichear.minesweepBackend.dto;

import lombok.Data;

import java.util.List;

@Data
public class DifficultyStatsVO {
    private int totalGames;
    private int totalWins;
    private double winRate;
    private int maxConsecutiveWins;
    private Long minDurationSeconds;
    private Long maxDurationSeconds;
    private List<TopRecordVO> topRecords;
}
