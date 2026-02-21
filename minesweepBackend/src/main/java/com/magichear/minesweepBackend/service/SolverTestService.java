package com.magichear.minesweepBackend.service;

import com.magichear.minesweepBackend.config.properties.SolverProperties;
import com.magichear.minesweepBackend.dto.AiTestStatusVO;
import com.magichear.minesweepBackend.dto.AiTestVO;
import com.magichear.minesweepBackend.dto.PredictionVO;
import com.magichear.minesweepBackend.entity.AiTestRecord;
import com.magichear.minesweepBackend.model.Difficulty;
import com.magichear.minesweepBackend.model.GameRule;
import com.magichear.minesweepBackend.repository.AiTestRecordRepository;
import com.magichear.minesweepBackend.solver.ExpertSolver;
import com.magichear.minesweepBackend.solver.game.MinesweeperGame;
import com.magichear.minesweepBackend.solver.game.SafeZoneGame;
import com.magichear.minesweepBackend.solver.game.SingleCellSafeGame;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Solver-based batch-testing service (replaces the old AI test service).
 * <p>
 * Runs 100 automated games using the {@link ExpertSolver}, collects statistics,
 * and writes one DB row.  Supports all three difficulties and both game rules.
 */
@Slf4j
@Service
public class SolverTestService {

    private final SolverAssistService solverAssistService;
    private final AiTestRecordRepository repository;

    private static final int TOTAL_GAMES = 100;
    private final Map<String, TestProgress> progressMap = new ConcurrentHashMap<>();
    private final ExecutorService testExecutor = Executors.newSingleThreadExecutor();

    public SolverTestService(SolverProperties solverProperties,
                             SolverAssistService solverAssistService,
                             AiTestRecordRepository repository) {
        this.solverAssistService = solverAssistService;
        this.repository = repository;
    }

    /**
     * Start an asynchronous batch test.
     *
     * @param testName   user-provided name for this test run
     * @param difficultyStr difficulty string (EASY, MEDIUM, HARD)
     * @param gameRuleStr game rule string (SAFE_ZONE, SINGLE_CELL_SAFE) — defaults to SAFE_ZONE
     * @param username    current user
     * @return tracking status
     */
    public AiTestStatusVO startTest(String testName, String difficultyStr,
                                     String gameRuleStr, String username) {
        Difficulty difficulty = Difficulty.valueOf(difficultyStr.toUpperCase());
        GameRule gameRule = parseGameRule(gameRuleStr);

        String trackingId = UUID.randomUUID().toString();
        TestProgress progress = new TestProgress();
        progress.setTrackingId(trackingId);
        progress.setGamesCompleted(0);
        progress.setTotalGames(TOTAL_GAMES);
        progress.setStatus("RUNNING");
        progressMap.put(trackingId, progress);

        testExecutor.submit(() -> runTest(trackingId, testName, difficulty, gameRule, username));

        AiTestStatusVO status = new AiTestStatusVO();
        status.setTrackingId(trackingId);
        status.setStatus("RUNNING");
        status.setProgress(0);
        status.setTotal(TOTAL_GAMES);
        return status;
    }

    public AiTestStatusVO getStatus(String trackingId) {
        TestProgress progress = progressMap.get(trackingId);
        if (progress == null) {
            throw new NoSuchElementException("Test not found: " + trackingId);
        }
        AiTestStatusVO status = new AiTestStatusVO();
        status.setTrackingId(progress.getTrackingId());
        status.setStatus(progress.getStatus());
        status.setProgress(progress.getGamesCompleted());
        status.setTotal(progress.getTotalGames());
        status.setResult(progress.getResult());
        status.setErrorMessage(progress.getErrorMessage());
        return status;
    }

    public List<AiTestVO> getAllTests() {
        return repository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toVO)
                .toList();
    }

    // ---- Async test runner ----

    void runTest(String trackingId, String testName, Difficulty difficulty,
                 GameRule gameRule, String username) {
        TestProgress progress = progressMap.get(trackingId);
        try {
            int wins = 0;
            long totalDurationMs = 0;
            long maxDurationMs = Long.MIN_VALUE;
            long minDurationMs = Long.MAX_VALUE;

            for (int i = 0; i < TOTAL_GAMES; i++) {
                GameResult result = simulateOneGame(difficulty, gameRule);
                if (result.won()) wins++;
                totalDurationMs += result.durationMs();
                maxDurationMs = Math.max(maxDurationMs, result.durationMs());
                minDurationMs = Math.min(minDurationMs, result.durationMs());
                progress.setGamesCompleted(i + 1);
            }

            AiTestRecord record = new AiTestRecord();
            record.setTestName(testName);
            record.setModelName("ExpertSolver");
            record.setDifficulty(difficulty);
            record.setTotalGames(TOTAL_GAMES);
            record.setWins(wins);
            record.setWinRate((double) wins / TOTAL_GAMES);
            record.setAvgDurationMs(totalDurationMs / TOTAL_GAMES);
            record.setMaxDurationMs(maxDurationMs);
            record.setMinDurationMs(minDurationMs);
            record.setUsername(username);
            record.setCreatedAt(LocalDateTime.now());
            record = repository.save(record);

            progress.setStatus("COMPLETED");
            progress.setResult(toVO(record));
            log.info("Solver test '{}' completed: difficulty={}, gameRule={}, winRate={}, avgDuration={}ms",
                    testName, difficulty, gameRule, record.getWinRate(), record.getAvgDurationMs());
        } catch (Exception e) {
            log.error("Solver test '{}' failed", testName, e);
            progress.setStatus("FAILED");
            progress.setErrorMessage(e.getMessage());
        }
    }

    // ---- Single-game simulation ----

    private GameResult simulateOneGame(Difficulty difficulty, GameRule gameRule) {
        Random rng = new Random();
        MinesweeperGame game = createGame(difficulty, gameRule, rng);

        long startTime = System.nanoTime();

        // First move: center (guaranteed safe in both rules)
        int centerRow = difficulty.getRows() / 2;
        int centerCol = difficulty.getCols() / 2;
        Boolean hit = game.guess(centerRow, centerCol);
        if (hit == Boolean.TRUE) {
            long durationMs = (System.nanoTime() - startTime) / 1_000_000;
            return new GameResult(false, durationMs);
        }
        if (game.isWon()) {
            long durationMs = (System.nanoTime() - startTime) / 1_000_000;
            return new GameResult(true, durationMs);
        }

        while (!game.isWon()) {
            PredictionVO prediction = solverAssistService.predict(game.view(), difficulty);
            int row = prediction.getSafestRow();
            int col = prediction.getSafestCol();

            if (row < 0 || col < 0) break; // No valid move

            hit = game.guess(row, col);
            if (hit == null) break; // No-op (already revealed)
            if (hit) break;         // Mine hit
            // Continue...
        }

        long durationMs = (System.nanoTime() - startTime) / 1_000_000;
        return new GameResult(game.isWon(), durationMs);
    }

    private MinesweeperGame createGame(Difficulty difficulty, GameRule gameRule, Random rng) {
        return switch (gameRule) {
            case SAFE_ZONE -> new SafeZoneGame(
                    difficulty.getRows(), difficulty.getCols(), difficulty.getMines(), rng);
            case SINGLE_CELL_SAFE -> new SingleCellSafeGame(
                    difficulty.getRows(), difficulty.getCols(), difficulty.getMines(), rng);
        };
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

    private AiTestVO toVO(AiTestRecord record) {
        AiTestVO vo = new AiTestVO();
        vo.setId(record.getId());
        vo.setTestName(record.getTestName());
        vo.setModelName(record.getModelName());
        vo.setDifficulty(record.getDifficulty().name());
        vo.setTotalGames(record.getTotalGames());
        vo.setWins(record.getWins());
        vo.setWinRate(record.getWinRate());
        vo.setAvgDurationMs(record.getAvgDurationMs());
        vo.setMaxDurationMs(record.getMaxDurationMs());
        vo.setMinDurationMs(record.getMinDurationMs());
        vo.setUsername(record.getUsername());
        vo.setCreatedAt(record.getCreatedAt());
        return vo;
    }

    // ---- Internal types ----

    @Data
    private static class TestProgress {
        private String trackingId;
        private int gamesCompleted;
        private int totalGames;
        private String status;
        private AiTestVO result;
        private String errorMessage;
    }

    private record GameResult(boolean won, long durationMs) {}
}
