package com.magichear.minesweepBackend.solver.utils;

import java.util.*;

/**
 * Gaussian elimination over integer-coefficient linear equations
 * to deduce deterministic safe / mine cells.
 */
public final class GaussianElimination {

    private GaussianElimination() {}

    /**
     * Perform Gaussian elimination on the given equations and mark any
     * cells that can be deterministically resolved.
     *
     * @return {@code true} if at least one new cell was resolved
     */
    public static boolean deduce(List<Equation> pending, Set<Long> knownMines, Set<Long> knownSafe) {
        if (pending.size() < 2) return false;

        Set<Long> allCells = new HashSet<>();
        for (Equation eq : pending) allCells.addAll(eq.cells());
        if (allCells.isEmpty()) return false;

        List<Long> cellList = new ArrayList<>(allCells);
        cellList.sort(Long::compareTo);
        Map<Long, Integer> cellToIdx = new HashMap<>();
        for (int i = 0; i < cellList.size(); i++) cellToIdx.put(cellList.get(i), i);

        int nVars = cellList.size();
        int nEqs = pending.size();

        long[][] matrix = new long[nEqs][nVars + 1];
        for (int e = 0; e < nEqs; e++) {
            Equation eq = pending.get(e);
            for (long cell : eq.cells()) {
                matrix[e][cellToIdx.get(cell)] = 1;
            }
            matrix[e][nVars] = eq.mines();
        }

        // Row reduction
        int pivotRow = 0;
        for (int col = 0; col < nVars; col++) {
            if (pivotRow >= nEqs) break;
            int best = -1;
            for (int r = pivotRow; r < nEqs; r++) {
                long v = Math.abs(matrix[r][col]);
                if (v > 0 && (best == -1 || v < Math.abs(matrix[best][col]))) {
                    best = r;
                }
            }
            if (best == -1) continue;
            if (best != pivotRow) {
                long[] tmp = matrix[pivotRow];
                matrix[pivotRow] = matrix[best];
                matrix[best] = tmp;
            }
            long pv = matrix[pivotRow][col];
            for (int r = 0; r < nEqs; r++) {
                if (r == pivotRow || matrix[r][col] == 0) continue;
                long f = matrix[r][col];
                for (int c = 0; c <= nVars; c++) {
                    matrix[r][c] = matrix[r][c] * pv - f * matrix[pivotRow][c];
                }
                // GCD normalise
                long g = 0;
                for (int c = 0; c <= nVars; c++) {
                    if (matrix[r][c] != 0) g = MathUtils.gcd(g, Math.abs(matrix[r][c]));
                }
                if (g > 1) {
                    for (int c = 0; c <= nVars; c++) matrix[r][c] /= g;
                }
            }
            pivotRow++;
        }

        // Analyse each reduced row
        boolean changed = false;
        for (long[] row : matrix) {
            List<int[]> active = new ArrayList<>(); // [varIdx, coeff]
            for (int i = 0; i < nVars; i++) {
                if (row[i] != 0) active.add(new int[]{i, (int) row[i]});
            }
            if (active.isEmpty()) continue;
            long rhs = row[nVars];

            long minLhs = 0, maxLhs = 0;
            for (int[] ac : active) {
                minLhs += Math.min(0, ac[1]);
                maxLhs += Math.max(0, ac[1]);
            }
            if (rhs < minLhs || rhs > maxLhs) continue;

            if (rhs == maxLhs) {
                for (int[] ac : active) {
                    long cell = cellList.get(ac[0]);
                    if (ac[1] > 0) {
                        if (knownMines.add(cell)) changed = true;
                    } else {
                        if (knownSafe.add(cell)) changed = true;
                    }
                }
                continue;
            }

            if (rhs == minLhs) {
                for (int[] ac : active) {
                    long cell = cellList.get(ac[0]);
                    if (ac[1] > 0) {
                        if (knownSafe.add(cell)) changed = true;
                    } else {
                        if (knownMines.add(cell)) changed = true;
                    }
                }
                continue;
            }

            // Per-cell feasibility
            for (int[] ac : active) {
                int ai = ac[1];
                long maxOthers = maxLhs - Math.max(0, ai);
                long minOthers = minLhs - Math.min(0, ai);
                boolean can0 = (minOthers <= rhs && rhs <= maxOthers);
                boolean can1 = (minOthers <= rhs - ai && rhs - ai <= maxOthers);
                long cell = cellList.get(ac[0]);
                if (!can0 && can1) {
                    if (knownMines.add(cell)) changed = true;
                } else if (can0 && !can1) {
                    if (knownSafe.add(cell)) changed = true;
                }
            }
        }
        return changed;
    }
}
