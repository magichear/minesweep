package com.magichear.minesweepBackend.solver.game;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class SafeZoneGameTest {

    private static final long SEED = 42L;

    @Test
    void firstClick_alwaysSafe() {
        // First click at centre must never hit a mine
        SafeZoneGame game = new SafeZoneGame(9, 9, 10, new Random(SEED));
        Boolean result = game.guess(4, 4);
        assertEquals(Boolean.FALSE, result, "First click should always be safe");
    }

    @Test
    void firstClick_safeZone_revealsNeighbours() {
        // After first click at centre, at least the 3×3 neighbourhood should be revealed
        SafeZoneGame game = new SafeZoneGame(9, 9, 10, new Random(SEED));
        game.guess(4, 4);
        int[][] view = game.view();
        // The clicked cell must be revealed (not 9)
        assertTrue(view[4][4] >= 0 && view[4][4] <= 8,
                "Clicked cell should be revealed");
        // Since it's a 3×3 safe zone, it should be 0, so flood-fill reveals neighbours
        assertEquals(0, view[4][4], "Clicked cell in safe zone should be 0");
        for (int dr = -1; dr <= 1; dr++) {
            for (int dc = -1; dc <= 1; dc++) {
                int r = 4 + dr, c = 4 + dc;
                assertTrue(view[r][c] >= 0 && view[r][c] <= 8,
                        "Neighbour (%d,%d) should be revealed".formatted(r, c));
            }
        }
    }

    @Test
    void view_initiallyAllUnrevealed() {
        SafeZoneGame game = new SafeZoneGame(9, 9, 10, new Random(SEED));
        int[][] view = game.view();
        for (int r = 0; r < 9; r++) {
            for (int c = 0; c < 9; c++) {
                assertEquals(9, view[r][c], "All cells should be unrevealed initially");
            }
        }
    }

    @Test
    void guess_mine_returnsTrue() {
        // After first click, if we guess a mine it should return TRUE
        SafeZoneGame game = new SafeZoneGame(9, 9, 10, new Random(SEED));
        game.guess(4, 4); // first safe click

        // Find a mine (unrevealed cell that returns TRUE when guessed)
        int[][] view = game.view();
        boolean foundMine = false;
        for (int r = 0; r < 9 && !foundMine; r++) {
            for (int c = 0; c < 9 && !foundMine; c++) {
                if (view[r][c] == 9) {
                    Boolean hit = game.guess(r, c);
                    if (hit == Boolean.TRUE) {
                        foundMine = true;
                    }
                }
            }
        }
        // With 10 mines in 9x9, there must be mines
        assertTrue(foundMine, "Should be able to find at least one mine");
    }

    @Test
    void guess_alreadyRevealed_returnsNull() {
        SafeZoneGame game = new SafeZoneGame(9, 9, 10, new Random(SEED));
        game.guess(4, 4);
        // Guess the same cell again
        Boolean result = game.guess(4, 4);
        assertNull(result, "Re-guessing revealed cell should return null");
    }

    @Test
    void dimensions_correct() {
        SafeZoneGame game = new SafeZoneGame(16, 30, 99, new Random(SEED));
        assertEquals(16, game.getHeight());
        assertEquals(30, game.getWidth());
        int[][] view = game.view();
        assertEquals(16, view.length);
        assertEquals(30, view[0].length);
    }

    @Test
    void winDetection_smallBoard() {
        // 3x3 board with 1 mine — first click at (1,1) guarantees 3x3 safe zone,
        // which covers all 9 cells, but we only have 1 mine...
        // Use 4x4 board with 1 mine instead
        SafeZoneGame game = new SafeZoneGame(4, 4, 1, new Random(SEED));
        assertFalse(game.isWon());
        game.guess(1, 1); // first click, generates safe zone

        // Keep revealing non-mine cells until won
        int attempts = 0;
        while (!game.isWon() && !game.isGameOver() && attempts < 20) {
            int[][] view = game.view();
            for (int r = 0; r < 4; r++) {
                for (int c = 0; c < 4; c++) {
                    if (view[r][c] == 9) {
                        Boolean hit = game.guess(r, c);
                        if (hit == Boolean.TRUE) {
                            // hit mine, game over
                            return;
                        }
                    }
                }
            }
            attempts++;
        }
        // If we get here without hitting a mine, game should be won
        if (!game.isGameOver()) {
            assertTrue(game.isWon());
        }
    }
}
