package com.magichear.minesweepBackend.solver.game;

import java.util.*;

/**
 * Minesweeper game environment that guarantees a <b>3×3 safe zone</b> on the first click.
 * <p>
 * The clicked cell and all 8 neighbours are mine-free, so the first click always
 * reveals a blank (0) cell and triggers a flood-fill cascade.
 * This mirrors the backend {@code GameService} rules.
 * <p>
 * Implements {@link MinesweeperGame} so it can be used interchangeably with
 * {@link SingleCellSafeGame} in solver evaluation and the main game flow.
 */
public class SafeZoneGame implements MinesweeperGame {

    private final int height;
    private final int width;
    private final int mineCount;
    private final Random rng;

    /** null until first click. -1 = mine, 0-8 = adjacent mine count. */
    private int[][] mineBoard;
    private final boolean[][] revealed;
    private boolean gameOver;
    private boolean won;

    public SafeZoneGame(int height, int width, int mineCount, Random rng) {
        this.height = height;
        this.width = width;
        this.mineCount = mineCount;
        this.rng = rng;
        this.revealed = new boolean[height][width];
    }

    // ---- Board generation (mirrors GameService.generateBoard) ----

    private void generateBoard(int safeRow, int safeCol) {
        mineBoard = new int[height][width];

        // 3×3 safe zone
        Set<Integer> safeZone = new HashSet<>();
        for (int dr = -1; dr <= 1; dr++) {
            for (int dc = -1; dc <= 1; dc++) {
                int nr = safeRow + dr, nc = safeCol + dc;
                if (nr >= 0 && nr < height && nc >= 0 && nc < width) {
                    safeZone.add(nr * width + nc);
                }
            }
        }

        int placed = 0;
        while (placed < mineCount) {
            int r = rng.nextInt(height);
            int c = rng.nextInt(width);
            int idx = r * width + c;
            if (!safeZone.contains(idx) && mineBoard[r][c] != -1) {
                mineBoard[r][c] = -1;
                placed++;
            }
        }

        // Compute numbers
        for (int r = 0; r < height; r++) {
            for (int c = 0; c < width; c++) {
                if (mineBoard[r][c] == -1) continue;
                int count = 0;
                for (int dr = -1; dr <= 1; dr++) {
                    for (int dc = -1; dc <= 1; dc++) {
                        int nr = r + dr, nc = c + dc;
                        if (nr >= 0 && nr < height && nc >= 0 && nc < width
                                && mineBoard[nr][nc] == -1) {
                            count++;
                        }
                    }
                }
                mineBoard[r][c] = count;
            }
        }
    }

    // ---- Flood reveal (mirrors GameService.floodReveal) ----

    private void floodReveal(int row, int col) {
        Queue<int[]> queue = new LinkedList<>();
        queue.add(new int[]{row, col});
        revealed[row][col] = true;

        while (!queue.isEmpty()) {
            int[] cell = queue.poll();
            int r = cell[0], c = cell[1];
            if (mineBoard[r][c] != 0) continue; // numbered cell – stop expanding
            for (int dr = -1; dr <= 1; dr++) {
                for (int dc = -1; dc <= 1; dc++) {
                    int nr = r + dr, nc = c + dc;
                    if (nr >= 0 && nr < height && nc >= 0 && nc < width
                            && !revealed[nr][nc] && mineBoard[nr][nc] != -1) {
                        revealed[nr][nc] = true;
                        queue.add(new int[]{nr, nc});
                    }
                }
            }
        }
    }

    // ---- Win detection ----

    private boolean checkWin() {
        for (int r = 0; r < height; r++) {
            for (int c = 0; c < width; c++) {
                if (mineBoard[r][c] != -1 && !revealed[r][c]) return false;
            }
        }
        return true;
    }

    // ---- MinesweeperGame API ----

    @Override
    public Boolean guess(int row, int col) {
        if (gameOver) return null;
        if (revealed[row][col]) return null;

        // First click – generate board with 3×3 safe zone
        if (mineBoard == null) {
            generateBoard(row, col);
        }

        if (mineBoard[row][col] == -1) {
            revealed[row][col] = true;
            gameOver = true;
            won = false;
            return Boolean.TRUE;
        }

        floodReveal(row, col);

        if (checkWin()) {
            gameOver = true;
            won = true;
        }
        return Boolean.FALSE;
    }

    @Override
    public boolean isWon() {
        return won;
    }

    public boolean isGameOver() {
        return gameOver;
    }

    @Override
    public int[][] view() {
        int[][] board = new int[height][width];
        for (int r = 0; r < height; r++) {
            for (int c = 0; c < width; c++) {
                board[r][c] = (revealed[r][c] && mineBoard != null) ? mineBoard[r][c] : 9;
            }
        }
        return board;
    }

    @Override
    public int getHeight() { return height; }

    @Override
    public int getWidth() { return width; }
}
