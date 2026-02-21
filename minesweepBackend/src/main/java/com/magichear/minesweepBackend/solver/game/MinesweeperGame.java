package com.magichear.minesweepBackend.solver.game;

/**
 * Common interface for a minesweeper game environment used by both the main
 * application and the solver evaluation suite.
 * <p>
 * Implementations encapsulate board generation, reveal logic, flood-fill,
 * and win detection, differing only in the first-click safety rule.
 */
public interface MinesweeperGame {

    /**
     * Reveal a cell at {@code (row, col)}.
     *
     * @return {@code Boolean.TRUE} if a mine was hit (game over),
     *         {@code Boolean.FALSE} if the cell was safely revealed,
     *         {@code null} if the cell was already revealed (no-op)
     */
    Boolean guess(int row, int col);

    /**
     * Board view in AI format: 0-8 for revealed cells, 9 for unrevealed.
     */
    int[][] view();

    /** Whether all non-mine cells have been revealed. */
    boolean isWon();

    int getHeight();

    int getWidth();
}
