package com.magichear.minesweepBackend.service;

import com.magichear.minesweepBackend.config.properties.SolverProperties;
import com.magichear.minesweepBackend.dto.PredictionVO;
import com.magichear.minesweepBackend.model.Difficulty;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SolverAssistServiceTest {

    private SolverAssistService service;

    @BeforeEach
    void setUp() {
        SolverProperties props = new SolverProperties();
        props.setMaxEnumCells(80);
        props.setMaxValidAssignments(200_000);
        service = new SolverAssistService(props);
    }

    @Test
    void predict_easy_returnsValidPrediction() {
        int[][] board = makePartialBoard(9, 9);
        PredictionVO result = service.predict(board, Difficulty.EASY);

        assertNotNull(result);
        assertNotNull(result.getProbabilities());
        assertEquals(9, result.getProbabilities().length);
        assertEquals(9, result.getProbabilities()[0].length);
        assertTrue(result.getSafestRow() >= 0 && result.getSafestRow() < 9);
        assertTrue(result.getSafestCol() >= 0 && result.getSafestCol() < 9);
    }

    @Test
    void predict_medium_returnsValidPrediction() {
        int[][] board = makePartialBoard(16, 16);
        PredictionVO result = service.predict(board, Difficulty.MEDIUM);

        assertNotNull(result);
        assertEquals(16, result.getProbabilities().length);
        assertEquals(16, result.getProbabilities()[0].length);
    }

    @Test
    void predict_hard_returnsValidPrediction() {
        int[][] board = makePartialBoard(16, 30);
        PredictionVO result = service.predict(board, Difficulty.HARD);

        assertNotNull(result);
        assertEquals(16, result.getProbabilities().length);
        assertEquals(30, result.getProbabilities()[0].length);
    }

    @Test
    void predict_allUnrevealed_returnsDefaultProbs() {
        int[][] board = new int[9][9];
        for (int r = 0; r < 9; r++)
            for (int c = 0; c < 9; c++)
                board[r][c] = 9;

        PredictionVO result = service.predict(board, Difficulty.EASY);
        assertNotNull(result);
        // Safest cell should be valid
        assertTrue(result.getSafestRow() >= 0);
        assertTrue(result.getSafestCol() >= 0);
    }

    @Test
    void predict_revealedCells_haveZeroProbability() {
        int[][] board = new int[9][9];
        for (int r = 0; r < 9; r++)
            for (int c = 0; c < 9; c++)
                board[r][c] = 9;
        // Reveal a few cells
        board[4][4] = 0;
        board[4][5] = 1;
        board[3][4] = 0;

        PredictionVO result = service.predict(board, Difficulty.EASY);
        assertEquals(0.0, result.getProbabilities()[4][4], "Revealed cell should have 0 probability");
        assertEquals(0.0, result.getProbabilities()[4][5], "Revealed cell should have 0 probability");
        assertEquals(0.0, result.getProbabilities()[3][4], "Revealed cell should have 0 probability");
    }

    /**
     * Create a partially revealed board with the center area revealed.
     */
    private int[][] makePartialBoard(int rows, int cols) {
        int[][] board = new int[rows][cols];
        for (int r = 0; r < rows; r++)
            for (int c = 0; c < cols; c++)
                board[r][c] = 9;

        // Reveal a small area in the center
        int cr = rows / 2, cc = cols / 2;
        board[cr][cc] = 0;
        board[cr - 1][cc] = 0;
        board[cr + 1][cc] = 1;
        board[cr][cc - 1] = 0;
        board[cr][cc + 1] = 1;
        return board;
    }
}
