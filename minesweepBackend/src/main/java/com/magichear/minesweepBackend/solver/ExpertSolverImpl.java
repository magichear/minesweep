package com.magichear.minesweepBackend.solver;

import com.magichear.minesweepBackend.solver.utils.*;

import java.util.*;

import static com.magichear.minesweepBackend.solver.utils.CellUtils.*;

/**
 * Default implementation of {@link ExpertSolver}.
 * <p>
 * Uses deterministic inference (basic / subset / overlap / Gaussian elimination),
 * exact probability via component enumeration + global DP weighting,
 * and heuristic fallback for oversized components.
 */
public class ExpertSolverImpl implements ExpertSolver {

    private final int h;
    private final int w;
    private final int totalMines;
    private final int maxEnumCells;
    private final int maxValidAssignments;

    private final Set<Long> knownMines = new HashSet<>();
    private final Set<Long> knownSafe = new HashSet<>();

    public ExpertSolverImpl(int h, int w, int totalMines) {
        this(h, w, totalMines, 100, 500_000);
    }

    public ExpertSolverImpl(int h, int w, int totalMines, int maxEnumCells, int maxValidAssignments) {
        this.h = h;
        this.w = w;
        this.totalMines = totalMines;
        this.maxEnumCells = maxEnumCells;
        this.maxValidAssignments = maxValidAssignments;
    }

    @Override
    public void reset() {
        knownMines.clear();
        knownSafe.clear();
    }

    // =================== Deterministic Inference ===================

    private List<Equation> buildEquations(int[][] view) {
        Map<Set<Long>, Integer> dedup = new HashMap<>();
        for (int r = 0; r < h; r++) {
            for (int c = 0; c < w; c++) {
                int val = view[r][c];
                if (val < 0 || val > 8) continue;

                Set<Long> unrev = new HashSet<>();
                int minesAround = 0;
                for (long[] nb : getNeighbors(r, c, h, w)) {
                    int nr = (int) nb[0], nc = (int) nb[1];
                    long nk = key(nr, nc, w);
                    if (knownMines.contains(nk)) {
                        minesAround++;
                    } else if (view[nr][nc] == 9 && !knownSafe.contains(nk)) {
                        unrev.add(nk);
                    }
                }
                if (unrev.isEmpty()) continue;

                int remMines = val - minesAround;
                if (remMines < 0) remMines = 0;
                if (remMines > unrev.size()) remMines = unrev.size();

                dedup.merge(unrev, remMines, (old, newVal) -> Math.min(old, newVal));
            }
        }
        List<Equation> eqs = new ArrayList<>(dedup.size());
        for (var entry : dedup.entrySet()) {
            eqs.add(new Equation(new HashSet<>(entry.getKey()), entry.getValue()));
        }
        return eqs;
    }

    private List<Equation> runDeterministicInference(int[][] view) {
        boolean changed = true;
        List<Equation> lastEqs = List.of();
        while (changed) {
            changed = false;
            List<Equation> equations = buildEquations(view);
            lastEqs = equations;

            // --- Global mine-count constraint analysis ---
            Set<Long> allFrontierCells = new HashSet<>();
            for (Equation eq : equations) allFrontierCells.addAll(eq.cells());

            int globalFrontierMinesExact = -1; // -1 = not exactly determined

            if (!allFrontierCells.isEmpty()) {
                int allTrulyUnknown = 0;
                for (int r = 0; r < h; r++) {
                    for (int c = 0; c < w; c++) {
                        if (view[r][c] == 9) {
                            long k = key(r, c, w);
                            if (!knownMines.contains(k) && !knownSafe.contains(k)) {
                                allTrulyUnknown++;
                            }
                        }
                    }
                }
                int frontierCount = allFrontierCells.size();
                int interiorCount = allTrulyUnknown - frontierCount;
                int remainingMines = totalMines - knownMines.size();
                if (remainingMines < 0) remainingMines = 0;

                int frontierMinesMin = Math.max(0, remainingMines - Math.max(interiorCount, 0));
                int frontierMinesMax = Math.min(frontierCount, remainingMines);

                // Direct deductions: all frontier cells safe / all mines
                if (frontierMinesMax == 0) {
                    for (long cell : allFrontierCells) {
                        if (knownSafe.add(cell)) changed = true;
                    }
                }
                if (frontierMinesMin == frontierCount && frontierCount > 0) {
                    for (long cell : allFrontierCells) {
                        if (knownMines.add(cell)) changed = true;
                    }
                }

                // Direct deductions: all interior cells safe / all mines
                int interiorMinesMax = Math.min(interiorCount, remainingMines);
                int interiorMinesMin = Math.max(0, remainingMines - frontierCount);

                if (interiorMinesMax == 0 && interiorCount > 0) {
                    for (int r = 0; r < h; r++) {
                        for (int c = 0; c < w; c++) {
                            if (view[r][c] == 9) {
                                long k = key(r, c, w);
                                if (!knownMines.contains(k) && !knownSafe.contains(k)
                                        && !allFrontierCells.contains(k)) {
                                    if (knownSafe.add(k)) changed = true;
                                }
                            }
                        }
                    }
                }
                if (interiorMinesMin == interiorCount && interiorCount > 0) {
                    for (int r = 0; r < h; r++) {
                        for (int c = 0; c < w; c++) {
                            if (view[r][c] == 9) {
                                long k = key(r, c, w);
                                if (!knownMines.contains(k) && !knownSafe.contains(k)
                                        && !allFrontierCells.contains(k)) {
                                    if (knownMines.add(k)) changed = true;
                                }
                            }
                        }
                    }
                }

                if (frontierMinesMin == frontierMinesMax) {
                    globalFrontierMinesExact = frontierMinesMin;
                }
            }

            if (changed) continue;

            // --- Basic single-point ---
            List<Equation> pending = new ArrayList<>();
            for (Equation eq : equations) {
                if (eq.mines() == 0) {
                    for (long cell : eq.cells()) {
                        if (knownSafe.add(cell)) changed = true;
                    }
                } else if (eq.mines() == eq.cells().size()) {
                    for (long cell : eq.cells()) {
                        if (knownMines.add(cell)) changed = true;
                    }
                } else {
                    pending.add(eq);
                }
            }

            if (changed) continue;

            // Pairwise subset reasoning
            int n = pending.size();
            outer:
            for (int i = 0; i < n; i++) {
                Set<Long> c1 = pending.get(i).cells();
                int m1 = pending.get(i).mines();
                for (int j = 0; j < n; j++) {
                    if (i == j) continue;
                    Set<Long> c2 = pending.get(j).cells();
                    int m2 = pending.get(j).mines();
                    if (c1.isEmpty() || c2.isEmpty() || c1.size() >= c2.size()) continue;
                    if (!c2.containsAll(c1)) continue;

                    Set<Long> diff = new HashSet<>(c2);
                    diff.removeAll(c1);
                    int diffMines = m2 - m1;
                    if (diffMines == 0) {
                        for (long cell : diff) {
                            if (knownSafe.add(cell)) changed = true;
                        }
                    } else if (diffMines == diff.size()) {
                        for (long cell : diff) {
                            if (knownMines.add(cell)) changed = true;
                        }
                    }
                    if (changed) break outer;
                }
            }

            // Generalized overlap reasoning (non-subset pairs)
            if (!changed && n <= 200) {
                outer2:
                for (int i = 0; i < n; i++) {
                    Set<Long> c1 = pending.get(i).cells();
                    int m1 = pending.get(i).mines();
                    for (int j = i + 1; j < n; j++) {
                        Set<Long> c2 = pending.get(j).cells();
                        int m2 = pending.get(j).mines();

                        Set<Long> overlap = new HashSet<>(c1);
                        overlap.retainAll(c2);
                        if (overlap.isEmpty()) continue;

                        Set<Long> d1 = new HashSet<>(c1);
                        d1.removeAll(c2);
                        Set<Long> d2 = new HashSet<>(c2);
                        d2.removeAll(c1);
                        if (d1.isEmpty() || d2.isEmpty()) continue; // subset handled above

                        int minOv = Math.max(0, Math.max(m1 - d1.size(), m2 - d2.size()));
                        int maxOv = Math.min(overlap.size(), Math.min(m1, m2));
                        if (minOv > maxOv) continue;

                        // Analyse d1
                        int minD1 = m1 - maxOv;
                        int maxD1 = m1 - minOv;
                        if (maxD1 == 0) {
                            for (long cell : d1) {
                                if (knownSafe.add(cell)) changed = true;
                            }
                        } else if (minD1 == d1.size()) {
                            for (long cell : d1) {
                                if (knownMines.add(cell)) changed = true;
                            }
                        }

                        // Analyse d2
                        int minD2 = m2 - maxOv;
                        int maxD2 = m2 - minOv;
                        if (maxD2 == 0) {
                            for (long cell : d2) {
                                if (knownSafe.add(cell)) changed = true;
                            }
                        } else if (minD2 == d2.size()) {
                            for (long cell : d2) {
                                if (knownMines.add(cell)) changed = true;
                            }
                        }

                        if (changed) break outer2;
                    }
                }
            }

            // Gaussian elimination (augmented with global mine-count equation)
            if (!changed) {
                List<Equation> gaussianInput = pending;
                if (globalFrontierMinesExact >= 0 && !allFrontierCells.isEmpty()
                        && globalFrontierMinesExact > 0
                        && globalFrontierMinesExact < allFrontierCells.size()) {
                    gaussianInput = new ArrayList<>(pending);
                    gaussianInput.add(new Equation(new HashSet<>(allFrontierCells), globalFrontierMinesExact));
                }
                if (GaussianElimination.deduce(gaussianInput, knownMines, knownSafe)) {
                    changed = true;
                }
            }
        }
        return lastEqs;
    }

    // =================== Opening move ===================

    private long bestOpeningCell() {
        return key(0, 0, w);
    }

    // =================== Public API ===================

    @Override
    public SolveResult solveStep(int[][] view) {
        List<Long> unrevealed = new ArrayList<>();
        for (int r = 0; r < h; r++) {
            for (int c = 0; c < w; c++) {
                if (view[r][c] == 9) unrevealed.add(key(r, c, w));
            }
        }
        if (unrevealed.isEmpty()) return new SolveResult("guess", null, null);

        if (unrevealed.size() == h * w) {
            long opening = bestOpeningCell();
            return new SolveResult("guess", new int[]{keyRow(opening, w), keyCol(opening, w)}, null);
        }

        List<Equation> equations = runDeterministicInference(view);

        // --- Iterative probability-inference feedback loop ---
        // After probability finds forced cells (prob 0 or 1), re-run inference
        // to discover further deterministic cells from the updated knowledge.
        Map<Long, Double> cellProbs = Map.of();
        for (int probInfIter = 0; probInfIter < 3; probInfIter++) {
            List<Long> hiddenUnknown = new ArrayList<>();
            for (long c : unrevealed) {
                if (!knownMines.contains(c) && view[keyRow(c, w)][keyCol(c, w)] == 9) {
                    hiddenUnknown.add(c);
                }
            }
            if (hiddenUnknown.isEmpty()) break;

            int knownMinesHidden = 0;
            for (long c : unrevealed) {
                if (knownMines.contains(c)) knownMinesHidden++;
            }
            int remainingUnknownMines = totalMines - knownMinesHidden;
            if (remainingUnknownMines < 0) remainingUnknownMines = 0;
            if (remainingUnknownMines > hiddenUnknown.size()) remainingUnknownMines = hiddenUnknown.size();

            cellProbs = GlobalProbabilityCalculator.calculate(
                    view, equations, hiddenUnknown, remainingUnknownMines,
                    h, w, maxEnumCells, maxValidAssignments, knownMines, knownSafe);

            if (cellProbs.isEmpty()) {
                cellProbs = HeuristicProbabilityCalculator.calculate(
                        view, hiddenUnknown, remainingUnknownMines, h, w, knownMines, knownSafe);
            }

            boolean newForced = false;
            for (var entry : cellProbs.entrySet()) {
                if (entry.getValue() <= 0.0) {
                    if (knownSafe.add(entry.getKey())) newForced = true;
                } else if (entry.getValue() >= 1.0) {
                    if (knownMines.add(entry.getKey())) newForced = true;
                }
            }

            if (!newForced) break; // no new info, stop iterating

            // Re-run deterministic inference with updated knowledge
            equations = runDeterministicInference(view);
        }

        // Pick safe cell with best cascade potential
        int[] safeCell = null;
        int bestPotentialMines = Integer.MAX_VALUE;
        for (long cell : knownSafe) {
            int cr = keyRow(cell, w), cc = keyCol(cell, w);
            if (view[cr][cc] != 9) continue;
            int potentialMineNeighbors = 0;
            for (long[] nb : getNeighbors(cr, cc, h, w)) {
                int nr = (int) nb[0], nc = (int) nb[1];
                long nk = key(nr, nc, w);
                if (view[nr][nc] == 9 && !knownSafe.contains(nk) && !knownMines.contains(nk)) {
                    potentialMineNeighbors++;
                }
            }
            if (safeCell == null || potentialMineNeighbors < bestPotentialMines) {
                bestPotentialMines = potentialMineNeighbors;
                safeCell = new int[]{cr, cc};
            }
        }

        // Return safe cell with full probability map for heatmap support
        if (safeCell != null) {
            return new SolveResult("safe", safeCell, cellProbs.isEmpty() ? null : cellProbs);
        }

        if (!cellProbs.isEmpty()) {
            // Compute effective score that accounts for information gain.
            // Frontier cells (with revealed neighbors) slightly reduce their effective
            // score because revealing them gives more constraint information,
            // potentially reducing future guesses.
            final double infoWeight = 0.20;
            final Map<Long, Double> cp = cellProbs;
            final Map<Long, Double> effectiveScore = new HashMap<>();
            for (var entry : cp.entrySet()) {
                long cell = entry.getKey();
                double prob = entry.getValue();
                int revealed = countRevealedNeighbors(view, cell, h, w);
                // Small reduction for frontier cells: more revealed neighbors → lower eff. score
                double score = prob * (1.0 - infoWeight * revealed / 8.0);
                effectiveScore.put(cell, score);
            }
            long bestCell = Collections.min(cp.keySet(), (a, b) -> {
                int cmp = Double.compare(effectiveScore.get(a), effectiveScore.get(b));
                if (cmp != 0) return cmp;
                // sub-tie-break on raw probability
                cmp = Double.compare(cp.get(a), cp.get(b));
                if (cmp != 0) return cmp;
                // Tie-break: prefer more revealed neighbors
                int revA = countRevealedNeighbors(view, a, h, w);
                int revB = countRevealedNeighbors(view, b, h, w);
                if (revA != revB) return revB - revA;
                // Tie-break: prefer fewer unrevealed neighbors
                int unrevA = countUnrevealedNeighbors(view, a, h, w);
                int unrevB = countUnrevealedNeighbors(view, b, h, w);
                if (unrevA != unrevB) return unrevA - unrevB;
                // Tie-break: prefer closer to center
                int distA = Math.abs(keyRow(a, w) - h / 2) + Math.abs(keyCol(a, w) - w / 2);
                int distB = Math.abs(keyRow(b, w) - h / 2) + Math.abs(keyCol(b, w) - w / 2);
                return distA - distB;
            });
            return new SolveResult("guess", new int[]{keyRow(bestCell, w), keyCol(bestCell, w)}, cellProbs);
        }

        // Last resort
        List<Long> validUnrevealed = new ArrayList<>();
        for (long c : unrevealed) {
            if (!knownMines.contains(c)) validUnrevealed.add(c);
        }
        if (!validUnrevealed.isEmpty()) {
            long bestCell = Collections.min(validUnrevealed, (a, b) -> {
                int distA = Math.abs(keyRow(a, w) - h / 2) + Math.abs(keyCol(a, w) - w / 2);
                int distB = Math.abs(keyRow(b, w) - h / 2) + Math.abs(keyCol(b, w) - w / 2);
                if (distA != distB) return distA - distB;
                int unrevA = countUnrevealedNeighbors(view, a, h, w);
                int unrevB = countUnrevealedNeighbors(view, b, h, w);
                return unrevA - unrevB;
            });
            return new SolveResult("guess", new int[]{keyRow(bestCell, w), keyCol(bestCell, w)}, null);
        }
        return new SolveResult("guess", null, null);
    }
}
