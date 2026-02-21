package com.magichear.minesweepBackend.solver;

import java.util.Map;

/**
 * Minesweeper solver interface.
 * <p>
 * Given a board view (0-8 for revealed cells, 9 for unrevealed), the solver
 * recommends the next action: open a safe cell, flag a mine, or guess the
 * cell with the lowest mine probability.
 */
public interface ExpertSolver {

    /**
     * Result of a single solve step.
     *
     * @param action    {@code "safe"}, {@code "mine"}, or {@code "guess"}
     * @param cell      target cell as {@code {row, col}}, or {@code null}
     * @param cellProbs probability map (cell-key -> mine probability) for unrevealed cells, or {@code null}
     */
    record SolveResult(String action, int[] cell, Map<Long, Double> cellProbs) {}

    /**
     * Choose the next action for the given board view.
     *
     * @param view board in AI format: 0-8 for revealed, 9 for unrevealed
     * @return the recommended action
     */
    SolveResult solveStep(int[][] view);

    /** Clear internal state for a new game. */
    void reset();
}
