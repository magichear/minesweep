package com.magichear.minesweepBackend.service;

import com.magichear.minesweepBackend.dto.GameStateVO;
import com.magichear.minesweepBackend.dto.PredictionVO;
import com.magichear.minesweepBackend.entity.GameRecord;
import com.magichear.minesweepBackend.model.Difficulty;
import com.magichear.minesweepBackend.model.GameRule;
import com.magichear.minesweepBackend.model.GameState;
import com.magichear.minesweepBackend.repository.GameRecordRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Core game logic: board generation, reveal, flag, win/loss detection.
 */
@Slf4j
@Service
public class GameService {

    private final Map<String, GameState> games = new ConcurrentHashMap<>();
    private final GameRecordRepository gameRecordRepository;
    private final SolverAssistService solverAssistService;

    public GameService(GameRecordRepository gameRecordRepository, SolverAssistService solverAssistService) {
        this.gameRecordRepository = gameRecordRepository;
        this.solverAssistService = solverAssistService;
    }

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    public GameStateVO createGame(String difficultyStr) {
        return createGame(difficultyStr, null);
    }

    public GameStateVO createGame(String difficultyStr, String gameRuleStr) {
        Difficulty difficulty = Difficulty.valueOf(difficultyStr.toUpperCase());
        GameRule gameRule = parseGameRule(gameRuleStr);
        GameState state = new GameState(difficulty, gameRule);
        games.put(state.getId(), state);
        log.info("Created game id={}, difficulty={}, gameRule={}", state.getId(), difficulty, gameRule);
        return toVO(state);
    }

    public GameStateVO revealCell(String gameId, int row, int col) {
        GameState state = getGame(gameId);
        validateBounds(state, row, col);

        if (state.isGameOver()) {
            log.warn("Attempted reveal on finished game {}", gameId);
            return toVO(state);
        }
        if (state.getRevealed()[row][col] || state.getFlagged()[row][col]) {
            return toVO(state);
        }

        // First click – generate the board
        if (!state.isStarted()) {
            generateBoard(state, row, col);
            state.setStarted(true);
            state.setStartTime(Instant.now());
            log.info("Game {} started – first click at ({},{}) gameRule={}", gameId, row, col, state.getGameRule());
        }

        // Stepped on a mine?
        if (state.getMineBoard()[row][col] == -1) {
            state.getRevealed()[row][col] = true;
            state.setGameOver(true);
            state.setWon(false);
            state.setDurationSeconds(elapsed(state));
            log.info("Game {} lost – mine at ({},{})", gameId, row, col);
            saveRecord(state);
            return toVO(state);
        }

        // Reveal (flood-fill if blank)
        floodReveal(state, row, col);

        // Win check
        if (checkWin(state)) {
            state.setGameOver(true);
            state.setWon(true);
            state.setDurationSeconds(elapsed(state));
            log.info("Game {} won in {}s", gameId, state.getDurationSeconds());
            saveRecord(state);
        }

        return toVO(state);
    }

    public GameStateVO toggleFlag(String gameId, int row, int col) {
        GameState state = getGame(gameId);
        validateBounds(state, row, col);

        if (state.isGameOver() || state.getRevealed()[row][col]) {
            return toVO(state);
        }
        state.getFlagged()[row][col] = !state.getFlagged()[row][col];
        log.debug("Game {} flag toggled at ({},{}) → {}", gameId, row, col,
                state.getFlagged()[row][col]);
        return toVO(state);
    }

    public PredictionVO predict(String gameId) {
        GameState state = getGame(gameId);
        if (state.isGameOver()) {
            throw new IllegalStateException("Game is already over");
        }
        if (!state.isStarted()) {
            throw new IllegalStateException("Game has not started yet");
        }
        return solverAssistService.predict(state.getAiBoard(), state.getDifficulty());
    }

    public GameStateVO getGameState(String gameId) {
        return toVO(getGame(gameId));
    }

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    private GameState getGame(String gameId) {
        GameState state = games.get(gameId);
        if (state == null) {
            throw new NoSuchElementException("Game not found: " + gameId);
        }
        return state;
    }

    private void validateBounds(GameState state, int row, int col) {
        if (row < 0 || row >= state.getRows() || col < 0 || col >= state.getCols()) {
            throw new IllegalArgumentException(
                    "Cell (%d,%d) out of bounds for %dx%d board".formatted(
                            row, col, state.getRows(), state.getCols()));
        }
    }

    /**
     * Generate mine board according to the game's rule.
     * <ul>
     *   <li>{@link GameRule#SAFE_ZONE}: 3×3 safe zone around the first click.</li>
     *   <li>{@link GameRule#SINGLE_CELL_SAFE}: only the clicked cell is guaranteed safe
     *       (mine is relocated if hit); board is pre-generated with random mines.</li>
     * </ul>
     */
    void generateBoard(GameState state, int safeRow, int safeCol) {
        if (state.getGameRule() == GameRule.SINGLE_CELL_SAFE) {
            generateBoardSingleCellSafe(state, safeRow, safeCol);
        } else {
            generateBoardSafeZone(state, safeRow, safeCol);
        }
    }

    private void generateBoardSafeZone(GameState state, int safeRow, int safeCol) {
        int rows = state.getRows();
        int cols = state.getCols();
        int[][] board = new int[rows][cols];

        // Safe zone = clicked cell + 8 neighbours
        Set<Integer> safeZone = new HashSet<>();
        for (int dr = -1; dr <= 1; dr++) {
            for (int dc = -1; dc <= 1; dc++) {
                int nr = safeRow + dr;
                int nc = safeCol + dc;
                if (nr >= 0 && nr < rows && nc >= 0 && nc < cols) {
                    safeZone.add(nr * cols + nc);
                }
            }
        }

        // Place mines
        Random rng = new Random();
        int placed = 0;
        while (placed < state.getTotalMines()) {
            int r = rng.nextInt(rows);
            int c = rng.nextInt(cols);
            int idx = r * cols + c;
            if (!safeZone.contains(idx) && board[r][c] != -1) {
                board[r][c] = -1;
                placed++;
            }
        }

        computeNumbers(board, rows, cols);
        state.setMineBoard(board);
        log.debug("Board generated (SafeZone) for game {}", state.getId());
    }

    private void generateBoardSingleCellSafe(GameState state, int safeRow, int safeCol) {
        int rows = state.getRows();
        int cols = state.getCols();
        int[][] board = new int[rows][cols];

        // Place mines randomly (no safe zone)
        Random rng = new Random();
        int placed = 0;
        while (placed < state.getTotalMines()) {
            int r = rng.nextInt(rows);
            int c = rng.nextInt(cols);
            if (board[r][c] != -1) {
                board[r][c] = -1;
                placed++;
            }
        }

        // If first click is a mine, relocate it
        if (board[safeRow][safeCol] == -1) {
            board[safeRow][safeCol] = 0;
            while (true) {
                int r = rng.nextInt(rows);
                int c = rng.nextInt(cols);
                if (board[r][c] != -1 && !(r == safeRow && c == safeCol)) {
                    board[r][c] = -1;
                    break;
                }
            }
        }

        computeNumbers(board, rows, cols);
        state.setMineBoard(board);
        log.debug("Board generated (SingleCellSafe) for game {}", state.getId());
    }

    private void computeNumbers(int[][] board, int rows, int cols) {
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (board[r][c] == -1) continue;
                int count = 0;
                for (int dr = -1; dr <= 1; dr++) {
                    for (int dc = -1; dc <= 1; dc++) {
                        int nr = r + dr;
                        int nc = c + dc;
                        if (nr >= 0 && nr < rows && nc >= 0 && nc < cols
                                && board[nr][nc] == -1) {
                            count++;
                        }
                    }
                }
                board[r][c] = count;
            }
        }
    }

    private GameRule parseGameRule(String gameRuleStr) {
        if (gameRuleStr == null || gameRuleStr.isBlank()) {
            return GameRule.SAFE_ZONE;
        }
        try {
            return GameRule.valueOf(gameRuleStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            return GameRule.SAFE_ZONE;
        }
    }

    /**
     * BFS flood-fill: reveal the cell; if it is blank (0), recursively reveal
     * all connected safe cells until bordered by numbered cells.
     */
    private void floodReveal(GameState state, int row, int col) {
        Queue<int[]> queue = new LinkedList<>();
        queue.add(new int[]{row, col});
        state.getRevealed()[row][col] = true;
        state.getFlagged()[row][col] = false;

        while (!queue.isEmpty()) {
            int[] cell = queue.poll();
            int r = cell[0], c = cell[1];

            if (state.getMineBoard()[r][c] != 0) continue; // numbered cell – stop expanding

            for (int dr = -1; dr <= 1; dr++) {
                for (int dc = -1; dc <= 1; dc++) {
                    int nr = r + dr;
                    int nc = c + dc;
                    if (nr >= 0 && nr < state.getRows() && nc >= 0 && nc < state.getCols()
                            && !state.getRevealed()[nr][nc]
                            && state.getMineBoard()[nr][nc] != -1) {
                        state.getRevealed()[nr][nc] = true;
                        state.getFlagged()[nr][nc] = false;
                        queue.add(new int[]{nr, nc});
                    }
                }
            }
        }
    }

    private boolean checkWin(GameState state) {
        int rows = state.getRows();
        int cols = state.getCols();
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (state.getMineBoard()[r][c] != -1 && !state.getRevealed()[r][c]) {
                    return false;
                }
            }
        }
        return true;
    }

    private long elapsed(GameState state) {
        return Instant.now().getEpochSecond() - state.getStartTime().getEpochSecond();
    }

    private void saveRecord(GameState state) {
        GameRecord record = new GameRecord();
        record.setDifficulty(state.getDifficulty());
        record.setWon(state.isWon());
        record.setDurationSeconds(state.getDurationSeconds());
        record.setPlayedAt(LocalDateTime.now());
        gameRecordRepository.save(record);
        log.info("Saved game record: difficulty={}, won={}, duration={}s",
                state.getDifficulty(), state.isWon(), state.getDurationSeconds());
    }

    private GameStateVO toVO(GameState state) {
        GameStateVO vo = new GameStateVO();
        vo.setGameId(state.getId());
        vo.setRows(state.getRows());
        vo.setCols(state.getCols());
        vo.setMines(state.getTotalMines());
        vo.setDifficulty(state.getDifficulty().name());
        vo.setGameRule(state.getGameRule().name());
        vo.setPlayerBoard(state.getPlayerBoard());
        vo.setFlagged(state.getFlagged());
        vo.setGameOver(state.isGameOver());
        vo.setWon(state.isWon());
        vo.setMinesRemaining(state.getRemainingMines());

        if (state.getStartTime() != null && !state.isGameOver()) {
            vo.setElapsedSeconds(elapsed(state));
        } else if (state.getDurationSeconds() != null) {
            vo.setElapsedSeconds(state.getDurationSeconds());
        }
        return vo;
    }
}
