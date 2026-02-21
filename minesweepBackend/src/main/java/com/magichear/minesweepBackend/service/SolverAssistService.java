package com.magichear.minesweepBackend.service;

import com.magichear.minesweepBackend.config.properties.SolverProperties;
import com.magichear.minesweepBackend.dto.PredictionVO;
import com.magichear.minesweepBackend.model.Difficulty;
import com.magichear.minesweepBackend.solver.ExpertSolver;
import com.magichear.minesweepBackend.solver.ExpertSolverImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Solver-based prediction service that replaces the old WebSocket AI service.
 * <p>
 * Uses {@link ExpertSolver} to analyse the current board state and produce
 * mine-probability predictions for all unrevealed cells.  A new solver instance
 * is created per request so that calls are stateless and thread-safe.
 */
@Slf4j
@Service
public class SolverAssistService {

    private final SolverProperties solverProperties;

    public SolverAssistService(SolverProperties solverProperties) {
        this.solverProperties = solverProperties;
    }

    /**
     * Analyse the board and return mine probabilities for every cell.
     *
     * @param aiBoard  board in AI format (0-8 revealed, 9 unrevealed)
     * @param difficulty  the game difficulty (provides dimensions and mine count)
     * @return prediction with probability grid and safest cell coordinates
     */
    public PredictionVO predict(int[][] aiBoard, Difficulty difficulty) {
        int rows = difficulty.getRows();
        int cols = difficulty.getCols();
        int mines = difficulty.getMines();

        ExpertSolver solver = new ExpertSolverImpl(
                rows, cols, mines,
                solverProperties.getMaxEnumCells(),
                solverProperties.getMaxValidAssignments());

        ExpertSolver.SolveResult result = solver.solveStep(aiBoard);

        // Build full probability grid – track which cells got solver data
        double[][] probs = new double[rows][cols];
        boolean[][] computed = new boolean[rows][cols];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (aiBoard[r][c] != 9) {
                    probs[r][c] = 0.0; // revealed cell
                    computed[r][c] = true;
                } else {
                    probs[r][c] = 1.0; // placeholder, will be overwritten
                }
            }
        }

        // Fill in computed probabilities from solver
        if (result.cellProbs() != null) {
            for (var entry : result.cellProbs().entrySet()) {
                long key = entry.getKey();
                int r = (int) (key / cols);
                int c = (int) (key % cols);
                if (aiBoard[r][c] == 9) {
                    probs[r][c] = entry.getValue();
                    computed[r][c] = true;
                }
            }
        }

        // Apply the solver's recommended cell
        if (result.cell() != null) {
            int cr = result.cell()[0], cc = result.cell()[1];
            if (aiBoard[cr][cc] == 9) {
                if ("safe".equals(result.action())) {
                    probs[cr][cc] = 0.0;
                } else if ("guess".equals(result.action())) {
                    probs[cr][cc] = Math.min(probs[cr][cc], 1e-6);
                }
                computed[cr][cc] = true;
            }
        }

        // Fill uncomputed unrevealed cells with a uniform default probability
        // based on remaining unaccounted mines distributed evenly.
        int uncomputedCount = 0;
        double computedMineSum = 0.0;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (aiBoard[r][c] == 9) {
                    if (computed[r][c]) {
                        computedMineSum += probs[r][c];
                    } else {
                        uncomputedCount++;
                    }
                }
            }
        }
        if (uncomputedCount > 0) {
            double remainingMines = Math.max(0, mines - computedMineSum);
            double defaultProb = Math.max(0.0, Math.min(1.0,
                    remainingMines / uncomputedCount));
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < cols; c++) {
                    if (aiBoard[r][c] == 9 && !computed[r][c]) {
                        probs[r][c] = defaultProb;
                    }
                }
            }
        }

        // Find safest unrevealed cell
        int safestRow = -1, safestCol = -1;
        double safestProb = Double.MAX_VALUE;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (aiBoard[r][c] == 9 && probs[r][c] < safestProb) {
                    safestProb = probs[r][c];
                    safestRow = r;
                    safestCol = c;
                }
            }
        }

        PredictionVO vo = new PredictionVO();
        vo.setProbabilities(probs);
        vo.setSafestRow(safestRow);
        vo.setSafestCol(safestCol);

        log.debug("Solver prediction: safest=({},{}) prob={}", safestRow, safestCol, safestProb);
        return vo;
    }
}
