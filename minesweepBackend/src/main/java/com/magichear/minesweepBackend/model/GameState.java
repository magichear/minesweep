package com.magichear.minesweepBackend.model;

import lombok.Data;

import java.time.Instant;
import java.util.UUID;

/**
 * In-memory representation of a single minesweeper game.
 */
@Data
public class GameState {

    private String id;
    private Difficulty difficulty;
    private GameRule gameRule;
    private int rows;
    private int cols;
    private int totalMines;

    /** Actual mine layout: -1 = mine, 0-8 = adjacent mine count. null before first click. */
    private int[][] mineBoard;
    private boolean[][] revealed;
    private boolean[][] flagged;

    private boolean gameOver;
    private boolean won;
    /** true after the first cell has been revealed */
    private boolean started;
    private Instant startTime;
    private Long durationSeconds;

    public GameState(Difficulty difficulty) {
        this(difficulty, GameRule.SAFE_ZONE);
    }

    public GameState(Difficulty difficulty, GameRule gameRule) {
        this.id = UUID.randomUUID().toString();
        this.difficulty = difficulty;
        this.gameRule = gameRule;
        this.rows = difficulty.getRows();
        this.cols = difficulty.getCols();
        this.totalMines = difficulty.getMines();
        this.revealed = new boolean[rows][cols];
        this.flagged = new boolean[rows][cols];
        this.gameOver = false;
        this.won = false;
        this.started = false;
    }

    /**
     * Board from the player's perspective.
     * <ul>
     *   <li>-2 = unrevealed</li>
     *   <li>-1 = mine (shown only on loss)</li>
     *   <li>0-8 = revealed number</li>
     * </ul>
     */
    public int[][] getPlayerBoard() {
        int[][] board = new int[rows][cols];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (revealed[r][c]) {
                    board[r][c] = mineBoard[r][c];
                } else if (gameOver && !won && mineBoard != null && mineBoard[r][c] == -1) {
                    board[r][c] = -1;
                } else {
                    board[r][c] = -2;
                }
            }
        }
        return board;
    }

    /**
     * Board in AI format: 0-8 for revealed cells, 9 for unrevealed.
     */
    public int[][] getAiBoard() {
        int[][] board = new int[rows][cols];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                board[r][c] = revealed[r][c] ? mineBoard[r][c] : 9;
            }
        }
        return board;
    }

    public int getRemainingMines() {
        int flagCount = 0;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (flagged[r][c]) flagCount++;
            }
        }
        return totalMines - flagCount;
    }
}
