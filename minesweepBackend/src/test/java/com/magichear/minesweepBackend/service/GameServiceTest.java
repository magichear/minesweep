package com.magichear.minesweepBackend.service;

import com.magichear.minesweepBackend.dto.GameStateVO;
import com.magichear.minesweepBackend.dto.PredictionVO;
import com.magichear.minesweepBackend.entity.GameRecord;
import com.magichear.minesweepBackend.model.Difficulty;
import com.magichear.minesweepBackend.model.GameRule;
import com.magichear.minesweepBackend.model.GameState;
import com.magichear.minesweepBackend.repository.GameRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
@ExtendWith(MockitoExtension.class)
class GameServiceTest {

    @Mock
    private GameRecordRepository gameRecordRepository;
    @Mock
    private SolverAssistService solverAssistService;

    private GameService gameService;

    @BeforeEach
    void setUp() {
        gameService = new GameService(gameRecordRepository, solverAssistService);
    }

    // ---- createGame ----

    @Test
    void createGame_easy() {
        GameStateVO vo = gameService.createGame("EASY");
        assertNotNull(vo.getGameId());
        assertEquals(9, vo.getRows());
        assertEquals(9, vo.getCols());
        assertEquals(10, vo.getMines());
        assertEquals("EASY", vo.getDifficulty());
        assertFalse(vo.isGameOver());
    }

    @Test
    void createGame_medium() {
        GameStateVO vo = gameService.createGame("MEDIUM");
        assertEquals(16, vo.getRows());
        assertEquals(16, vo.getCols());
        assertEquals(40, vo.getMines());
    }

    @Test
    void createGame_hard() {
        GameStateVO vo = gameService.createGame("HARD");
        assertEquals(16, vo.getRows());
        assertEquals(30, vo.getCols());
        assertEquals(99, vo.getMines());
    }

    @Test
    void createGame_invalidDifficulty() {
        assertThrows(IllegalArgumentException.class, () -> gameService.createGame("INVALID"));
    }

    // ---- first click ----

    @Test
    void firstClick_isSafe() {
        GameStateVO initial = gameService.createGame("EASY");
        GameStateVO revealed = gameService.revealCell(initial.getGameId(), 4, 4);
        assertTrue(revealed.getPlayerBoard()[4][4] >= 0, "First click must be safe");
        assertFalse(revealed.isGameOver());
    }

    @Test
    void firstClick_isBlank() {
        GameStateVO initial = gameService.createGame("EASY");
        GameStateVO revealed = gameService.revealCell(initial.getGameId(), 4, 4);
        assertEquals(0, revealed.getPlayerBoard()[4][4], "First click must be blank (0)");
    }

    @Test
    void firstClick_floodFill() {
        GameStateVO initial = gameService.createGame("EASY");
        GameStateVO revealed = gameService.revealCell(initial.getGameId(), 4, 4);
        int count = 0;
        for (int[] row : revealed.getPlayerBoard()) {
            for (int v : row) {
                if (v >= 0) count++;
            }
        }
        assertTrue(count > 1, "Flood fill should reveal multiple cells");
    }

    // ---- flag ----

    @Test
    void toggleFlag() {
        GameStateVO initial = gameService.createGame("EASY");
        GameStateVO flagged = gameService.toggleFlag(initial.getGameId(), 0, 0);
        assertTrue(flagged.getFlagged()[0][0]);
        GameStateVO unflagged = gameService.toggleFlag(initial.getGameId(), 0, 0);
        assertFalse(unflagged.getFlagged()[0][0]);
    }

    @Test
    void cannotRevealFlaggedCell() {
        GameStateVO initial = gameService.createGame("EASY");
        // Start the game first
        GameStateVO started = gameService.revealCell(initial.getGameId(), 4, 4);

        int targetRow = -1;
        int targetCol = -1;
        for (int r = 0; r < started.getRows() && targetRow == -1; r++) {
            for (int c = 0; c < started.getCols(); c++) {
                if (started.getPlayerBoard()[r][c] == -2) {
                    targetRow = r;
                    targetCol = c;
                    break;
                }
            }
        }
        assertTrue(targetRow >= 0, "Should find at least one unrevealed cell after first click");

        // Flag an unrevealed cell
        gameService.toggleFlag(initial.getGameId(), targetRow, targetCol);
        // Attempt to reveal it – should remain unrevealed
        GameStateVO vo = gameService.revealCell(initial.getGameId(), targetRow, targetCol);
        assertEquals(-2, vo.getPlayerBoard()[targetRow][targetCol]);
    }

    @Test
    void minesRemaining_decreasesWithFlag() {
        GameStateVO initial = gameService.createGame("EASY");
        assertEquals(10, initial.getMinesRemaining());
        GameStateVO flagged = gameService.toggleFlag(initial.getGameId(), 0, 0);
        assertEquals(9, flagged.getMinesRemaining());
    }

    // ---- predict ----

    @Test
    void predict_notStarted() {
        GameStateVO initial = gameService.createGame("EASY");
        assertThrows(IllegalStateException.class,
                () -> gameService.predict(initial.getGameId()));
    }

    @Test
    void predict_hardDifficulty_works() {
        PredictionVO expected = new PredictionVO();
        expected.setSafestRow(8);
        expected.setSafestCol(15);
        expected.setProbabilities(new double[16][30]);
        when(solverAssistService.predict(any(int[][].class), any(Difficulty.class))).thenReturn(expected);

        GameStateVO initial = gameService.createGame("HARD");
        gameService.revealCell(initial.getGameId(), 8, 15);
        PredictionVO result = gameService.predict(initial.getGameId());
        assertEquals(8, result.getSafestRow());
        assertEquals(15, result.getSafestCol());
        verify(solverAssistService).predict(any(int[][].class), any(Difficulty.class));
    }

    @Test
    void predict_mediumDifficulty_delegatesToSolver() {
        PredictionVO expected = new PredictionVO();
        expected.setSafestRow(5);
        expected.setSafestCol(5);
        expected.setProbabilities(new double[16][16]);
        when(solverAssistService.predict(any(int[][].class), any(Difficulty.class))).thenReturn(expected);

        GameStateVO initial = gameService.createGame("MEDIUM");
        gameService.revealCell(initial.getGameId(), 8, 8);

        PredictionVO result = gameService.predict(initial.getGameId());
        assertEquals(5, result.getSafestRow());
        assertEquals(5, result.getSafestCol());
        verify(solverAssistService).predict(any(int[][].class), any(Difficulty.class));
    }

    @Test
    void predict_delegatesToSolver() {
        PredictionVO expected = new PredictionVO();
        expected.setSafestRow(1);
        expected.setSafestCol(2);
        expected.setProbabilities(new double[9][9]);
        when(solverAssistService.predict(any(int[][].class), any(Difficulty.class))).thenReturn(expected);

        GameStateVO initial = gameService.createGame("EASY");
        gameService.revealCell(initial.getGameId(), 4, 4);

        PredictionVO result = gameService.predict(initial.getGameId());
        assertEquals(1, result.getSafestRow());
        assertEquals(2, result.getSafestCol());
        verify(solverAssistService).predict(any(int[][].class), any(Difficulty.class));
    }

    // ---- edge cases ----

    @Test
    void gameNotFound() {
        assertThrows(NoSuchElementException.class, () -> gameService.revealCell("no-such-id", 0, 0));
    }

    @Test
    void outOfBounds() {
        GameStateVO initial = gameService.createGame("EASY");
        assertThrows(IllegalArgumentException.class,
                () -> gameService.revealCell(initial.getGameId(), -1, 0));
        assertThrows(IllegalArgumentException.class,
                () -> gameService.revealCell(initial.getGameId(), 0, 99));
    }

    @Test
    void revealAlreadyRevealed_noop() {
        GameStateVO initial = gameService.createGame("EASY");
        gameService.revealCell(initial.getGameId(), 4, 4);
        // Revealing the same cell again should be a no-op
        GameStateVO again = gameService.revealCell(initial.getGameId(), 4, 4);
        assertFalse(again.isGameOver());
    }

    // ---- board generation determinism ----

    @Test
    void generateBoard_safeZone() {
        GameState state = new GameState(Difficulty.EASY);
        gameService.generateBoard(state, 4, 4);
        int[][] board = state.getMineBoard();
        // The clicked cell and all 8 neighbours must be mine-free
        for (int dr = -1; dr <= 1; dr++) {
            for (int dc = -1; dc <= 1; dc++) {
                assertNotEquals(-1, board[4 + dr][4 + dc],
                        "Safe zone cell (%d,%d) must not be a mine".formatted(4 + dr, 4 + dc));
            }
        }
    }

    @Test
    void generateBoard_correctMineCount() {
        GameState state = new GameState(Difficulty.EASY);
        gameService.generateBoard(state, 0, 0);
        int mines = 0;
        for (int[] row : state.getMineBoard()) {
            for (int v : row) {
                if (v == -1) mines++;
            }
        }
        assertEquals(10, mines);
    }

    @Test
    void generateBoard_cornerClick() {
        GameState state = new GameState(Difficulty.EASY);
        gameService.generateBoard(state, 0, 0);
        assertEquals(0, state.getMineBoard()[0][0], "Corner first click must be blank");
    }

    // ---- win detection ----

    @Test
    void winGame_recordSaved() {
        GameStateVO initial = gameService.createGame("EASY");
        // Reveal first cell to start.
        gameService.revealCell(initial.getGameId(), 4, 4);
        // Since first reveal won't end the game, save should not have been called
        verify(gameRecordRepository, never()).save(any(GameRecord.class));
    }

    // ---- MEDIUM difficulty board generation ----

    @Test
    void generateBoard_medium_correctMineCount() {
        GameState state = new GameState(Difficulty.MEDIUM);
        gameService.generateBoard(state, 8, 8);
        int mines = 0;
        for (int[] row : state.getMineBoard()) {
            for (int v : row) {
                if (v == -1) mines++;
            }
        }
        assertEquals(40, mines, "MEDIUM board should have 40 mines");
    }

    @Test
    void generateBoard_medium_safeZone() {
        GameState state = new GameState(Difficulty.MEDIUM);
        gameService.generateBoard(state, 8, 8);
        int[][] board = state.getMineBoard();
        for (int dr = -1; dr <= 1; dr++) {
            for (int dc = -1; dc <= 1; dc++) {
                assertNotEquals(-1, board[8 + dr][8 + dc],
                        "Safe zone cell (%d,%d) must not be a mine".formatted(8 + dr, 8 + dc));
            }
        }
    }

    @Test
    void firstClick_medium_isSafe() {
        GameStateVO initial = gameService.createGame("MEDIUM");
        GameStateVO revealed = gameService.revealCell(initial.getGameId(), 8, 8);
        assertTrue(revealed.getPlayerBoard()[8][8] >= 0, "First click must be safe");
        assertFalse(revealed.isGameOver());
    }

    @Test
    void firstClick_medium_floodFill() {
        GameStateVO initial = gameService.createGame("MEDIUM");
        GameStateVO revealed = gameService.revealCell(initial.getGameId(), 8, 8);
        int count = 0;
        for (int[] row : revealed.getPlayerBoard()) {
            for (int v : row) {
                if (v >= 0) count++;
            }
        }
        assertTrue(count > 1, "Flood fill should reveal multiple cells on medium board");
    }

    @Test
    void getAiBoard_medium_correctFormat() {
        GameState state = new GameState(Difficulty.MEDIUM);
        gameService.generateBoard(state, 8, 8);
        // No cells revealed yet, all should be 9
        int[][] aiBoard = state.getAiBoard();
        assertEquals(16, aiBoard.length, "AI board should have 16 rows");
        assertEquals(16, aiBoard[0].length, "AI board should have 16 cols");
        for (int r = 0; r < 16; r++) {
            for (int c = 0; c < 16; c++) {
                assertEquals(9, aiBoard[r][c], "Unrevealed cells should be 9");
            }
        }
    }

    // ---- GameRule tests ----

    @Test
    void createGame_withGameRule_safeZone() {
        GameStateVO vo = gameService.createGame("EASY", "SAFE_ZONE");
        assertEquals("SAFE_ZONE", vo.getGameRule());
    }

    @Test
    void createGame_withGameRule_singleCellSafe() {
        GameStateVO vo = gameService.createGame("EASY", "SINGLE_CELL_SAFE");
        assertEquals("SINGLE_CELL_SAFE", vo.getGameRule());
    }

    @Test
    void createGame_nullGameRule_defaultsSafeZone() {
        GameStateVO vo = gameService.createGame("EASY", null);
        assertEquals("SAFE_ZONE", vo.getGameRule());
    }

    @Test
    void createGame_defaultGameRule_safeZone() {
        GameStateVO vo = gameService.createGame("EASY");
        assertEquals("SAFE_ZONE", vo.getGameRule());
    }

    @Test
    void generateBoard_singleCellSafe_correctMineCount() {
        GameState state = new GameState(Difficulty.EASY, GameRule.SINGLE_CELL_SAFE);
        gameService.generateBoard(state, 4, 4);
        int mines = 0;
        for (int[] row : state.getMineBoard()) {
            for (int v : row) {
                if (v == -1) mines++;
            }
        }
        assertEquals(10, mines, "Single-cell-safe board should have 10 mines");
    }

    @Test
    void generateBoard_singleCellSafe_firstClickSafe() {
        GameState state = new GameState(Difficulty.EASY, GameRule.SINGLE_CELL_SAFE);
        gameService.generateBoard(state, 4, 4);
        assertNotEquals(-1, state.getMineBoard()[4][4],
                "First click cell must not be a mine in single-cell-safe mode");
    }

    @Test
    void firstClick_singleCellSafe_isSafe() {
        GameStateVO initial = gameService.createGame("EASY", "SINGLE_CELL_SAFE");
        GameStateVO revealed = gameService.revealCell(initial.getGameId(), 4, 4);
        assertTrue(revealed.getPlayerBoard()[4][4] >= 0, "First click must be safe");
        assertFalse(revealed.isGameOver());
    }
}
