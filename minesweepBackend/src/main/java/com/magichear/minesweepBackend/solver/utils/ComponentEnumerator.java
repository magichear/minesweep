package com.magichear.minesweepBackend.solver.utils;

import java.util.*;

/**
 * Enumerates all valid mine/safe assignments within a single connected
 * component using constrained backtracking with MCV (most-constrained-variable)
 * ordering.
 */
public final class ComponentEnumerator {

    private ComponentEnumerator() {}

    /**
     * Statistics collected by enumeration: for each possible mine-count k,
     * how many valid assignments exist globally and per cell.
     *
     * @param cells      the ordered list of cells in this component
     * @param validTotal total number of valid assignments found
     * @param byK        k → count-of-assignments-with-exactly-k-mines
     * @param cellByK    cell → (k → count-of-assignments-where-cell-is-mine-with-k-total-mines)
     */
    public record CompStats(
            List<Long> cells,
            int validTotal,
            Map<Integer, Long> byK,
            Map<Long, Map<Integer, Long>> cellByK
    ) {}

    /**
     * Enumerate all valid assignments for the given component.
     *
     * @return {@code null} if the component is too large or no valid assignments exist
     */
    public static CompStats enumerate(Set<Long> compCells, List<Equation> compEqs,
                                      int maxEnumCells, int maxValidAssignments) {
        return enumerate(compCells, compEqs, maxEnumCells, maxValidAssignments, compCells.size());
    }

    /**
     * Enumerate all valid assignments for the given component with global mine-count pruning.
     *
     * @param maxMines upper bound on mines in this component (from global constraint)
     * @return {@code null} if the component is too large or no valid assignments exist
     */
    public static CompStats enumerate(Set<Long> compCells, List<Equation> compEqs,
                                      int maxEnumCells, int maxValidAssignments, int maxMines) {
        if (compCells.size() > maxEnumCells) return null;

        // Prepare equation data
        Map<Long, List<Integer>> eqIndicesPerCell = new HashMap<>();
        int[] eqTargets = new int[compEqs.size()];
        int[] totalCellsInEq = new int[compEqs.size()];

        for (int ei = 0; ei < compEqs.size(); ei++) {
            Equation eq = compEqs.get(ei);
            eqTargets[ei] = eq.mines();
            totalCellsInEq[ei] = eq.cells().size();
            for (long cell : eq.cells()) {
                eqIndicesPerCell.computeIfAbsent(cell, k -> new ArrayList<>()).add(ei);
            }
        }

        // MCV ordering
        List<Long> compList = new ArrayList<>(compCells);
        compList.sort(Comparator.<Long>comparingInt(a -> eqIndicesPerCell.getOrDefault(a, List.of()).size()).reversed()
                .thenComparing(Comparator.naturalOrder()));

        int nEqs = compEqs.size();
        int[] assignedMines = new int[nEqs];
        int[] assignedCount = new int[nEqs];

        Map<Integer, Long> byK = new HashMap<>();
        Map<Long, Map<Integer, Long>> cellByK = new HashMap<>();
        for (long cell : compList) cellByK.put(cell, new HashMap<>());

        int[] assignment = new int[compList.size()];
        long[] validTotal = {0};
        int[] nodes = {0};
        int maxNodes = 5_000_000;

        long[] compArr = new long[compList.size()];
        for (int i = 0; i < compList.size(); i++) compArr[i] = compList.get(i);

        int[][] relatedEqs = new int[compList.size()][];
        for (int i = 0; i < compList.size(); i++) {
            List<Integer> rel = eqIndicesPerCell.getOrDefault(compArr[i], List.of());
            relatedEqs[i] = new int[rel.size()];
            for (int j = 0; j < rel.size(); j++) relatedEqs[i][j] = rel.get(j);
        }

        backtrack(0, 0, compArr, relatedEqs, assignedMines, assignedCount,
                totalCellsInEq, eqTargets, nEqs, assignment, byK, cellByK,
                validTotal, nodes, maxNodes, maxValidAssignments, maxMines);

        if (validTotal[0] == 0) return null;

        return new CompStats(compList, (int) validTotal[0], byK, cellByK);
    }

    // ---------- internal backtracking ----------

    private static void backtrack(int idx, int minesUsed, long[] compArr, int[][] relatedEqs,
                                  int[] assignedMines, int[] assignedCount, int[] totalCells,
                                  int[] eqTargets, int nEqs, int[] assignment,
                                  Map<Integer, Long> byK, Map<Long, Map<Integer, Long>> cellByK,
                                  long[] validTotal, int[] nodes, int maxNodes,
                                  int maxValidAssignments, int maxMines) {
        nodes[0]++;
        if (validTotal[0] >= maxValidAssignments || nodes[0] >= maxNodes) return;
        if (minesUsed > maxMines) return;  // global mine-count pruning

        if (idx == compArr.length) {
            for (int ei = 0; ei < nEqs; ei++) {
                if (assignedMines[ei] != eqTargets[ei]) return;
            }
            validTotal[0]++;
            byK.merge(minesUsed, 1L, (old, newVal) -> old + newVal);
            for (int i = 0; i < compArr.length; i++) {
                if (assignment[i] == 1) {
                    cellByK.get(compArr[i]).merge(minesUsed, 1L, (old, newVal) -> old + newVal);
                }
            }
            return;
        }

        int[] rels = relatedEqs[idx];

        // Try assignment = 0
        assignment[idx] = 0;
        boolean ok = true;
        int touched0 = 0;
        for (int ri = 0; ri < rels.length; ri++) {
            int ei = rels[ri];
            assignedCount[ei]++;
            touched0 = ri + 1;
            if (!isStillPossible(ei, assignedMines, assignedCount, totalCells, eqTargets)) {
                ok = false;
                break;
            }
        }
        if (ok) {
            backtrack(idx + 1, minesUsed, compArr, relatedEqs, assignedMines, assignedCount,
                    totalCells, eqTargets, nEqs, assignment, byK, cellByK,
                    validTotal, nodes, maxNodes, maxValidAssignments, maxMines);
        }
        for (int ri = 0; ri < touched0; ri++) {
            assignedCount[rels[ri]]--;
        }

        // Try assignment = 1
        assignment[idx] = 1;
        ok = true;
        int touched1 = 0;
        for (int ri = 0; ri < rels.length; ri++) {
            int ei = rels[ri];
            assignedCount[ei]++;
            assignedMines[ei]++;
            touched1 = ri + 1;
            if (!isStillPossible(ei, assignedMines, assignedCount, totalCells, eqTargets)) {
                ok = false;
                break;
            }
        }
        if (ok) {
            backtrack(idx + 1, minesUsed + 1, compArr, relatedEqs, assignedMines, assignedCount,
                    totalCells, eqTargets, nEqs, assignment, byK, cellByK,
                    validTotal, nodes, maxNodes, maxValidAssignments, maxMines);
        }
        for (int ri = 0; ri < touched1; ri++) {
            assignedCount[rels[ri]]--;
            assignedMines[rels[ri]]--;
        }
    }

    private static boolean isStillPossible(int ei, int[] assignedMines, int[] assignedCount,
                                           int[] totalCells, int[] eqTargets) {
        int unassigned = totalCells[ei] - assignedCount[ei];
        return assignedMines[ei] <= eqTargets[ei]
                && assignedMines[ei] + unassigned >= eqTargets[ei];
    }
}
