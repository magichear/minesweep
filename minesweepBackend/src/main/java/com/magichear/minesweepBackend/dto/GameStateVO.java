package com.magichear.minesweepBackend.dto;

import lombok.Data;

@Data
public class GameStateVO {
    private String gameId;
    private int rows;
    private int cols;
    private int mines;
    private String difficulty;
    private String gameRule;
    private int[][] playerBoard;
    private boolean[][] flagged;
    private boolean gameOver;
    private boolean won;
    private long elapsedSeconds;
    private int minesRemaining;
}
