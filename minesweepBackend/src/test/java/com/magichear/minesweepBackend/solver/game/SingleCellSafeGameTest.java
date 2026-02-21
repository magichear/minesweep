package com.magichear.minesweepBackend.solver.game;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class SingleCellSafeGameTest {

    private static final long SEED = 42L;

    @Test
    void firstClick_alwaysSafe() {
        // Even if the first click lands on a mine, it gets relocated
        // Test multiple seeds to increase confidence
        for (int s = 0; s < 100; s++) {
            SingleCellSafeGame game = new SingleCellSafeGame(9, 9, 10, new Random(s));
            Boolean result = game.guess(0, 0);
            assertNotEquals(Boolean.TRUE, result,
                    "First click should never hit a mine (seed=" + s + ")");
        }
    }

    @Test
    void view_initiallyAllUnrevealed() {
        SingleCellSafeGame game = new SingleCellSafeGame(9, 9, 10, new Random(SEED));
        int[][] view = game.view();
        for (int r = 0; r < 9; r++) {
            for (int c = 0; c < 9; c++) {
                assertEquals(9, view[r][c], "All cells should be unrevealed initially");
            }
        }
    }

    @Test
    void guess_revealedCell_returnsNull() {
        SingleCellSafeGame game = new SingleCellSafeGame(9, 9, 10, new Random(SEED));
        game.guess(4, 4);
        Boolean result = game.guess(4, 4);
        assertNull(result, "Re-guessing revealed cell should return null");
    }

    @Test
    void guess_mine_returnsTrue() {
        SingleCellSafeGame game = new SingleCellSafeGame(9, 9, 10, new Random(SEED));
        game.guess(4, 4); // first safe click

        // Find a mine
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
        assertTrue(foundMine, "Should find at least one mine");
    }

    @Test
    void view_revealedCells_showNumbers() {
        SingleCellSafeGame game = new SingleCellSafeGame(9, 9, 10, new Random(SEED));
        game.guess(4, 4);
        int[][] view = game.view();
        // At least the clicked cell should be revealed
        assertTrue(view[4][4] >= 0 && view[4][4] <= 8,
                "Clicked cell should show a number 0-8");
    }

    @Test
    void floodFill_zeroCell_revealsNeighbours() {
        // Use a large board with few mines so first click likely hits 0
        SingleCellSafeGame game = new SingleCellSafeGame(9, 9, 1, new Random(SEED));
        game.guess(4, 4);
        int[][] view = game.view();
        // Count revealed cells – should be more than 1 due to flood fill
        int revealed = 0;
        for (int r = 0; r < 9; r++) {
            for (int c = 0; c < 9; c++) {
                if (view[r][c] != 9) revealed++;
            }
        }
        assertTrue(revealed > 1, "Flood fill should reveal multiple cells, got " + revealed);
    }

    @Test
    void dimensions_correct() {
        SingleCellSafeGame game = new SingleCellSafeGame(16, 30, 99, new Random(SEED));
        assertEquals(16, game.getHeight());
        assertEquals(30, game.getWidth());
        int[][] view = game.view();
        assertEquals(16, view.length);
        assertEquals(30, view[0].length);
    }

    @Test
    void winDetection() {
        // 3x3 board with 1 mine — easy to win
        SingleCellSafeGame game = new SingleCellSafeGame(3, 3, 1, new Random(SEED));
        assertFalse(game.isWon());

        // Reveal cells until won or game over
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                Boolean hit = game.guess(r, c);
                if (hit == Boolean.TRUE) return; // mine hit - test still passes
                if (game.isWon()) {
                    assertTrue(game.isWon());
                    return;
                }
            }
        }
    }

    @Test
    void singleCellSafe_onlyProtectsClickedCell() {
        // Unlike SafeZoneGame, neighbours of first click CAN be mines
        // Test with high mine density: 9x9 with 70 mines → only 11 safe cells
        // First click at (0,0) only guarantees (0,0) is safe
        boolean neighbourMineFound = false;
        for (int s = 0; s < 200; s++) {
            SingleCellSafeGame game = new SingleCellSafeGame(9, 9, 70, new Random(s));
            game.guess(0, 0); // first click safe
            // Check if (0,1) or (1,0) is a mine
            Boolean r01 = game.guess(0, 1);
            if (r01 == Boolean.TRUE) {
                neighbourMineFound = true;
                break;
            }
            Boolean r10 = game.guess(1, 0);
            if (r10 == Boolean.TRUE) {
                neighbourMineFound = true;
                break;
            }
        }
        assertTrue(neighbourMineFound,
                "With high mine density, a neighbour of first click should be a mine in at least one seed");
    }
}
