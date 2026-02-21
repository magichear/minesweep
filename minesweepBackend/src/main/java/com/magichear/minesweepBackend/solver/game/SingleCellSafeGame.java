package com.magichear.minesweepBackend.solver.game;

import java.util.*;

/**
 * Minesweeper game environment — direct port of Python {@code train/game.py}.
 * <p>
 * Rules:
 * <ul>
 *   <li>Mines are placed randomly at construction time.</li>
 *   <li>If the very first guess hits a mine, the mine is relocated (first-click safe,
 *       but only for the single clicked cell).</li>
 *   <li>Revealing a zero-count cell floods (auto-reveals) all neighbours recursively.</li>
 *   <li>The game is won when all non-mine cells are revealed.</li>
 * </ul>
 * Implements {@link MinesweeperGame} so it can be used interchangeably with
 * {@link SafeZoneGame} in solver evaluation and the main game flow.
 */
public class SingleCellSafeGame implements MinesweeperGame {

    private final int height;
    private final int width;
    private final Set<Long> guessed = new HashSet<>();
    private final Set<Long> mines = new HashSet<>();
    private final Random rng;

    public SingleCellSafeGame(int height, int width, int mineCount, Random rng) {
        this.height = height;
        this.width = width;
        this.rng = rng;
        addMines(mineCount);
    }

    private long key(int r, int c) {
        return (long) r * width + c;
    }

    private void addMines(int count) {
        for (int i = 0; i < count; i++) {
            while (true) {
                int r = rng.nextInt(height);
                int c = rng.nextInt(width);
                long k = key(r, c);
                if (!mines.contains(k)) {
                    mines.add(k);
                    break;
                }
            }
        }
    }

    @Override
    public Boolean guess(int r, int c) {
        long k = key(r, c);
        if (guessed.isEmpty() && mines.contains(k)) {
            // First click safe: relocate the mine
            while (mines.contains(k)) {
                mines.remove(k);
                addMines(1);
            }
        }
        if (mines.contains(k)) return Boolean.TRUE;
        if (guessed.contains(k)) return null;
        spread(r, c);
        return Boolean.FALSE;
    }

    private int countNearbyMines(int r, int c) {
        int count = 0;
        for (int dr = -1; dr <= 1; dr++) {
            for (int dc = -1; dc <= 1; dc++) {
                int nr = r + dr, nc = c + dc;
                if (nr >= 0 && nr < height && nc >= 0 && nc < width) {
                    if (mines.contains(key(nr, nc))) count++;
                }
            }
        }
        return count;
    }

    private void spread(int r, int c) {
        long k = key(r, c);
        if (guessed.contains(k)) return;
        guessed.add(k);
        if (countNearbyMines(r, c) > 0) return;
        for (int dr = -1; dr <= 1; dr++) {
            for (int dc = -1; dc <= 1; dc++) {
                int nr = r + dr, nc = c + dc;
                if (nr >= 0 && nr < height && nc >= 0 && nc < width) {
                    spread(nr, nc);
                }
            }
        }
    }

    @Override
    public boolean isWon() {
        return guessed.size() + mines.size() == height * width;
    }

    @Override
    public int[][] view() {
        int[][] board = new int[height][width];
        for (int r = 0; r < height; r++) {
            Arrays.fill(board[r], 9);
        }
        for (long k : guessed) {
            int r = (int) (k / width);
            int c = (int) (k % width);
            board[r][c] = countNearbyMines(r, c);
        }
        return board;
    }

    @Override
    public int getHeight() { return height; }

    @Override
    public int getWidth() { return width; }
}
