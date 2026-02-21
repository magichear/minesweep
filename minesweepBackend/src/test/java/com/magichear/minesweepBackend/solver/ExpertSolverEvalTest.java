package com.magichear.minesweepBackend.solver;

import com.magichear.minesweepBackend.solver.game.MinesweeperGame;
import com.magichear.minesweepBackend.solver.game.SafeZoneGame;
import com.magichear.minesweepBackend.solver.game.SingleCellSafeGame;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Evaluation suite for {@link ExpertSolver} / {@link ExpertSolverImpl}.
 * <p>
 * Tagged with {@code "solver-eval"} so it is <b>excluded</b> from the default
 * {@code mvn test} run (to avoid slowing down CI builds) and can be executed
 * independently via:
 * <pre>
 *   mvn test -Dgroups=solver-eval
 * </pre>
 */
@Tag("solver-eval")
class ExpertSolverEvalTest {

    // ----------- difficulty specs (mirrors Python config.py) -----------

    record DifficultySpec(String name, int height, int width, int mines,
                          double minWinRate) {}

    private static final DifficultySpec[] DIFFICULTIES = {
            new DifficultySpec("beginner",     9,  9,  10, 88.0),
            new DifficultySpec("intermediate", 16, 16, 40, 73.0),
            new DifficultySpec("expert",       16, 30, 99, 38.0),
    };

    private static final int NUM_GAMES = 1000;
    private static final long SEED = 20260221L;

    // ================= evaluation engine =================

    /**
     * Run a single game using the {@link MinesweeperGame} interface and return
     * whether the solver won.
     */
    private boolean runSingleGame(ExpertSolver solver, MinesweeperGame game) {
        solver.reset();

        int[] firstMove = chooseMove(solver, game.view());
        if (firstMove == null) {
            firstMove = new int[]{game.getHeight() / 2, game.getWidth() / 2};
        }
        Boolean hit = game.guess(firstMove[0], firstMove[1]);
        if (hit == Boolean.TRUE) return false;
        if (game.isWon()) return true;

        while (true) {
            int[][] board = game.view();
            int[] move = chooseMove(solver, board);
            if (move == null) return game.isWon();

            hit = game.guess(move[0], move[1]);
            if (hit == null) return game.isWon();
            if (hit) return false;
            if (game.isWon()) return true;
        }
    }

    /**
     * Mirrors Python predict_mines + choose_move: builds a probability array
     * and picks the unrevealed cell with the lowest mine probability.
     */
    private int[] chooseMove(ExpertSolver solver, int[][] board) {
        int h = board.length, w = board[0].length;
        double[][] probs = new double[h][w];
        for (int r = 0; r < h; r++) {
            for (int c = 0; c < w; c++) {
                probs[r][c] = 1.0;
            }
        }

        ExpertSolver.SolveResult result = solver.solveStep(board);

        if (result.cellProbs() != null) {
            for (var entry : result.cellProbs().entrySet()) {
                long key = entry.getKey();
                int r = (int) (key / w);
                int c = (int) (key % w);
                if (board[r][c] == 9) {
                    probs[r][c] = entry.getValue();
                }
            }
        }

        if (result.cell() != null) {
            int cr = result.cell()[0], cc = result.cell()[1];
            if (board[cr][cc] == 9) {
                if ("safe".equals(result.action())) {
                    probs[cr][cc] = 0.0;
                } else if ("guess".equals(result.action())) {
                    probs[cr][cc] = Math.min(probs[cr][cc], 1e-6);
                }
            }
        }

        // Pick unrevealed cell with lowest probability
        int bestR = -1, bestC = -1;
        double bestP = Double.MAX_VALUE;
        for (int r = 0; r < h; r++) {
            for (int c = 0; c < w; c++) {
                if (board[r][c] == 9 && probs[r][c] < bestP) {
                    bestP = probs[r][c];
                    bestR = r;
                    bestC = c;
                }
            }
        }
        if (bestR == -1) return null;
        return new int[]{bestR, bestC};
    }

    /**
     * Evaluate the solver on a single difficulty and return the win rate %.
     */
    private double evaluate(DifficultySpec spec) {
        return evaluate(spec, false);
    }

    /**
     * Evaluate the solver on a single difficulty, optionally using the backend game environment.
     *
     * @param useBackendEnv if true, use {@link SafeZoneGame} (3×3 safe zone);
     *                      if false, use {@link SingleCellSafeGame} (single-cell safe)
     */
    private double evaluate(DifficultySpec spec, boolean useBackendEnv) {
        String envLabel = useBackendEnv ? "Backend" : "SolverTest";
        Random masterRng = new Random(SEED);
        ExpertSolver solver = new ExpertSolverImpl(spec.height, spec.width, spec.mines);

        int wins = 0;
        long t0 = System.nanoTime();
        for (int i = 0; i < NUM_GAMES; i++) {
            Random gameRng = new Random(masterRng.nextLong());
            MinesweeperGame game = useBackendEnv
                    ? new SafeZoneGame(spec.height, spec.width, spec.mines, gameRng)
                    : new SingleCellSafeGame(spec.height, spec.width, spec.mines, gameRng);
            if (runSingleGame(solver, game)) wins++;
        }
        long elapsed = System.nanoTime() - t0;

        double winRate = 100.0 * wins / NUM_GAMES;
        double avgMs = (elapsed / 1e6) / NUM_GAMES;

        System.out.printf("""
                
                === ExpertSolver Evaluation [%s] ===
                Difficulty: %s
                Board: %dx%d, mines=%d
                Games: %d
                Wins: %d
                Win rate: %.2f%%
                Average time per game: %.3f ms
                Total wall time: %.3f s
                """,
                envLabel, spec.name, spec.height, spec.width, spec.mines,
                NUM_GAMES, wins, winRate, avgMs, elapsed / 1e9);

        return winRate;
    }

    // ================= tests (one per difficulty) =================
    @Test
    void evaluateAllAndPrintSummary() {
        System.out.println("\n=== Summary (All Difficulties) ===");
        for (DifficultySpec spec : DIFFICULTIES) {
            double winRate = evaluate(spec);
            System.out.printf("- %-12s | %2dx%2d / %2d | win_rate=%.2f%%%n",
                    spec.name, spec.height, spec.width, spec.mines, winRate);
            assertTrue(winRate >= spec.minWinRate,
                    "%s win rate %.2f%% < threshold %.2f%%".formatted(spec.name, winRate, spec.minWinRate));
        }
    }

    /**
     * Compare solver performance between the two game environments:
     * <ul>
     *   <li><b>SolverTest</b> ({@link SingleCellSafeGame}) — first click only guarantees the
     *       clicked cell is safe (mine relocated if hit); mirrors Python {@code game.py}.</li>
     *   <li><b>Backend</b> ({@link SafeZoneGame}) — first click guarantees a 3×3 safe zone
     *       (clicked cell + 8 neighbours mine-free), so the first click always reveals a
     *       blank (0) cell and triggers a larger initial cascade; mirrors {@code GameService}.</li>
     * </ul>
     */
    @Test
    void compareEnvironments() {
        System.out.println("\n╔═══════════════════════════════════════════════════════════════════╗");
        System.out.println("║     ExpertSolver — Environment Comparison (SolverTest vs Backend) ║");
        System.out.println("╚═══════════════════════════════════════════════════════════════════╝");

        record EnvResult(String envName, String difficulty, double winRate, double avgMs) {}
        List<EnvResult> results = new java.util.ArrayList<>();

        for (DifficultySpec spec : DIFFICULTIES) {
            double solverTestWR = evaluate(spec, false);
            results.add(new EnvResult("SolverTest", spec.name, solverTestWR, 0));

            double backendWR = evaluate(spec, true);
            results.add(new EnvResult("Backend", spec.name, backendWR, 0));
        }

        // Print comparison table
        System.out.println("\n┌──────────────┬───────────────────────┬───────────────────────┬──────────┐");
        System.out.println("│ Difficulty    │ SolverTest (1-cell)   │ Backend (3×3 safe)    │ Δ (pp)   │");
        System.out.println("├──────────────┼───────────────────────┼───────────────────────┼──────────┤");

        for (DifficultySpec spec : DIFFICULTIES) {
            double stWR = results.stream()
                    .filter(r -> r.envName.equals("SolverTest") && r.difficulty.equals(spec.name))
                    .findFirst().orElseThrow().winRate;
            double beWR = results.stream()
                    .filter(r -> r.envName.equals("Backend") && r.difficulty.equals(spec.name))
                    .findFirst().orElseThrow().winRate;
            double delta = beWR - stWR;
            System.out.printf("│ %-12s │       %6.2f%%          │       %6.2f%%          │ %+6.2f   │%n",
                    spec.name, stWR, beWR, delta);
        }

        System.out.println("└──────────────┴───────────────────────┴───────────────────────┴──────────┘");
        System.out.println("""
                
                Legend:
                  SolverTest = first click: single cell safe (mine relocated); Python game.py port
                  Backend    = first click: 3×3 safe zone, always blank (0); GameService rules
                  Δ (pp)     = Backend win rate − SolverTest win rate (percentage points)
                """);
    }
}
