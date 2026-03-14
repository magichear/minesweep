package com.magichear.minesweepBackend.external.service;

import com.magichear.minesweepBackend.config.properties.SolverProperties;
import com.magichear.minesweepBackend.external.dto.AnalyzeResponse;
import com.magichear.minesweepBackend.external.recognition.BoardRecognizer;
import com.magichear.minesweepBackend.external.recognition.BoardRecognizer.RecognitionResult;
import com.magichear.minesweepBackend.solver.ExpertSolver;
import com.magichear.minesweepBackend.solver.ExpertSolverImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Orchestrates external minesweeper assistance:
 * screen capture → recognition → solver → heatmap/auto-play.
 */
@Slf4j
@Service
public class ExternalAssistService {

    private final ScreenCaptureService captureService;
    private final SolverProperties solverProperties;
    private final BoardRecognizer recognizer = new BoardRecognizer();

    public ExternalAssistService(ScreenCaptureService captureService,
                                 SolverProperties solverProperties) {
        this.captureService = captureService;
        this.solverProperties = solverProperties;
    }

    /**
     * Capture a screen region, recognize the board, run the solver, and return predictions.
     */
    public AnalyzeResponse analyzeRegion(int x, int y, int width, int height,
                                         int expectedRows, int expectedCols, int mines,
                                         boolean debugMode) throws AWTException {
        // 1. Capture screen
        BufferedImage capture = captureService.captureRegion(x, y, width, height, debugMode);

        // 2. Recognize
        RecognitionResult recResult = recognizer.recognize(capture);

        List<String> warnings = new ArrayList<>(recResult.warnings());

        // 3. Calibration check
        boolean calibrationOk = true;
        if (recResult.rows() != expectedRows || recResult.cols() != expectedCols) {
            calibrationOk = false;
            warnings.add(String.format("Expected %dx%d board but detected %dx%d. " +
                            "Please adjust the capture region.",
                    expectedRows, expectedCols, recResult.rows(), recResult.cols()));
        }

        if (recResult.rows() == 0 || recResult.cols() == 0) {
            return new AnalyzeResponse(0, 0, new int[0][0], new double[0][0],
                    -1, -1, "error", warnings, false);
        }

        // 4. Run solver
        int[][] aiBoard = BoardRecognizer.toAiBoard(recResult.board());
        int rows = recResult.rows();
        int cols = recResult.cols();

        // Debug logging
        if (debugMode) {
            captureService.saveDebugLog(
                    "Board (" + rows + "x" + cols + "):\n" +
                    BoardRecognizer.boardToString(recResult.board()),
                    "recognition"
            );
        }

        return runSolver(rows, cols, mines, aiBoard, recResult.board(), warnings, calibrationOk, debugMode);
    }

    /**
     * Analyze from a pre-loaded image (for testing).
     */
    public AnalyzeResponse analyzeImage(BufferedImage image, int expectedRows, int expectedCols,
                                        int mines, boolean debugMode) {
        RecognitionResult recResult = recognizer.recognize(image);
        List<String> warnings = new ArrayList<>(recResult.warnings());

        boolean calibrationOk = true;
        if (recResult.rows() != expectedRows || recResult.cols() != expectedCols) {
            calibrationOk = false;
            warnings.add(String.format("Expected %dx%d board but detected %dx%d.",
                    expectedRows, expectedCols, recResult.rows(), recResult.cols()));
        }

        if (recResult.rows() == 0 || recResult.cols() == 0) {
            return new AnalyzeResponse(0, 0, new int[0][0], new double[0][0],
                    -1, -1, "error", warnings, false);
        }

        int[][] aiBoard = BoardRecognizer.toAiBoard(recResult.board());
        return runSolver(recResult.rows(), recResult.cols(), mines,
                aiBoard, recResult.board(), warnings, calibrationOk, debugMode);
    }

    private AnalyzeResponse runSolver(int rows, int cols, int mines,
                                      int[][] aiBoard, int[][] board,
                                      List<String> warnings, boolean calibrationOk,
                                      boolean debugMode) {
        ExpertSolver solver = new ExpertSolverImpl(
                rows, cols, mines,
                solverProperties.getMaxEnumCells(),
                solverProperties.getMaxValidAssignments());

        ExpertSolver.SolveResult result = solver.solveStep(aiBoard);

        // Build probability grid
        double[][] probs = new double[rows][cols];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (aiBoard[r][c] != 9) {
                    probs[r][c] = 0.0;
                } else {
                    probs[r][c] = 1.0;
                }
            }
        }

        if (result.cellProbs() != null) {
            for (var entry : result.cellProbs().entrySet()) {
                long key = entry.getKey();
                int r = (int) (key / cols);
                int c = (int) (key % cols);
                if (aiBoard[r][c] == 9) {
                    probs[r][c] = entry.getValue();
                }
            }
        }

        int safestRow = -1, safestCol = -1;
        String action = "none";
        if (result.cell() != null) {
            safestRow = result.cell()[0];
            safestCol = result.cell()[1];
            action = result.action();
            if ("safe".equals(action)) {
                probs[safestRow][safestCol] = 0.0;
            }
        }

        // Fill uncomputed cells with uniform default
        boolean[][] computed = new boolean[rows][cols];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (aiBoard[r][c] != 9) {
                    computed[r][c] = true;
                }
            }
        }
        if (result.cellProbs() != null) {
            for (long key : result.cellProbs().keySet()) {
                int r = (int) (key / cols);
                int c = (int) (key % cols);
                computed[r][c] = true;
            }
        }
        int uncomputedCount = 0;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (!computed[r][c]) uncomputedCount++;
            }
        }
        if (uncomputedCount > 0) {
            int revealedCount = 0;
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < cols; c++) {
                    if (aiBoard[r][c] != 9) revealedCount++;
                }
            }
            int totalCells = rows * cols;
            int unrevealed = totalCells - revealedCount;
            double defaultProb = unrevealed > 0 ? (double) mines / unrevealed : 0.5;
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < cols; c++) {
                    if (!computed[r][c]) {
                        probs[r][c] = defaultProb;
                    }
                }
            }
        }

        if (debugMode) {
            StringBuilder sb = new StringBuilder("Solver result:\n");
            sb.append("Action: ").append(action).append("\n");
            if (safestRow >= 0) {
                sb.append("Safest: (").append(safestRow).append(",").append(safestCol).append(")\n");
            }
            sb.append("Probabilities:\n");
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < cols; c++) {
                    if (c > 0) sb.append(" ");
                    sb.append(String.format("%.2f", probs[r][c]));
                }
                sb.append("\n");
            }
            captureService.saveDebugLog(sb.toString(), "solver");
        }

        return new AnalyzeResponse(rows, cols, board, probs, safestRow, safestCol,
                action, warnings, calibrationOk);
    }

    /**
     * Click at a cell position on screen, given the capture region and cell coordinates.
     */
    public void clickCell(int captureX, int captureY,
                          RecognitionResult recResult,
                          int cellRow, int cellCol,
                          boolean rightClick) throws AWTException {
        var region = recResult.cellRegions()[cellRow][cellCol];
        int screenX = captureX + region.centerX();
        int screenY = captureY + region.centerY();
        captureService.clickAt(screenX, screenY, rightClick);
    }
}
