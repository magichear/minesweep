package com.magichear.minesweepBackend.solver.utils;

import java.util.*;

import static com.magichear.minesweepBackend.solver.utils.CellUtils.*;

/**
 * Constraint-aware heuristic that estimates mine probabilities using
 * local neighbour constraints and a global prior, as a fallback when
 * exact enumeration is infeasible.
 */
public final class HeuristicProbabilityCalculator {

    private HeuristicProbabilityCalculator() {}

    /**
     * Estimate mine probability for each cell in {@code candidateCells}.
     *
     * @param view                  board (0-8 revealed, 9 unrevealed)
     * @param candidateCells        the cells whose probabilities are requested
     * @param remainingUnknownMines estimated total remaining mines among the candidates
     * @param h                     board height
     * @param w                     board width
     * @param knownMines            known mine cell keys
     * @param knownSafe             known safe cell keys
     * @return cell-key → estimated mine probability
     */
    public static Map<Long, Double> calculate(int[][] view, List<Long> candidateCells,
                                              double remainingUnknownMines,
                                              int h, int w,
                                              Set<Long> knownMines, Set<Long> knownSafe) {
        if (candidateCells.isEmpty()) return Map.of();

        double globalPrior = remainingUnknownMines / candidateCells.size();
        Map<Long, Double> rawProbs = new HashMap<>();
        Map<Long, Integer> supportCounts = new HashMap<>();

        for (long cellKey : candidateCells) {
            int cr = keyRow(cellKey, w), cc = keyCol(cellKey, w);
            double weightedSum = 0.0, totalW = 0.0;
            int support = 0;

            for (long[] nb : getNeighbors(cr, cc, h, w)) {
                int nr = (int) nb[0], nc = (int) nb[1];
                int val = view[nr][nc];
                if (val < 0 || val > 8) continue;

                int unrevCnt = 0, minesAround = 0;
                for (long[] nb2 : getNeighbors(nr, nc, h, w)) {
                    int ar = (int) nb2[0], ac = (int) nb2[1];
                    long ak = key(ar, ac, w);
                    if (knownMines.contains(ak)) {
                        minesAround++;
                    } else if (view[ar][ac] == 9 && !knownSafe.contains(ak)) {
                        unrevCnt++;
                    }
                }
                if (unrevCnt <= 0) continue;

                int rem = val - minesAround;
                if (rem < 0) rem = 0;
                if (rem > unrevCnt) rem = unrevCnt;

                double localP = (double) rem / unrevCnt;
                double wt = 1.0 / unrevCnt;
                weightedSum += localP * wt;
                totalW += wt;
                support++;
            }

            rawProbs.put(cellKey, totalW > 0 ? weightedSum / totalW : globalPrior);
            supportCounts.put(cellKey, support);
        }

        double avgRaw = rawProbs.values().stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double scale = (avgRaw > 1e-12) ? (globalPrior / avgRaw) : 1.0;

        Map<Long, Double> probs = new HashMap<>();
        for (var entry : rawProbs.entrySet()) {
            double scaled = Math.max(0.0, Math.min(1.0, entry.getValue() * scale));
            double alpha = Math.min(0.85, 0.35 + 0.15 * supportCounts.get(entry.getKey()));
            double mixed = alpha * scaled + (1.0 - alpha) * globalPrior;
            probs.put(entry.getKey(), Math.max(0.0, Math.min(1.0, mixed)));
        }
        return probs;
    }
}
