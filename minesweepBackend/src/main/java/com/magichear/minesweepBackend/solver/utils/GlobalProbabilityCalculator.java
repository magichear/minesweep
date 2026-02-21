package com.magichear.minesweepBackend.solver.utils;

import com.magichear.minesweepBackend.solver.utils.ComponentDiscovery.Component;
import com.magichear.minesweepBackend.solver.utils.ComponentEnumerator.CompStats;

import java.math.BigInteger;
import java.util.*;

import static com.magichear.minesweepBackend.solver.utils.MathUtils.comb;

/**
 * Computes exact, globally-weighted mine probabilities across all
 * independent components using dynamic programming with {@link BigInteger}
 * arithmetic to avoid overflow.
 * <p>
 * When some components are too large to enumerate, a partial fallback is
 * applied: successfully-enumerated components keep exact probabilities while
 * failed components fall back to {@link HeuristicProbabilityCalculator}.
 */
public final class GlobalProbabilityCalculator {

    private GlobalProbabilityCalculator() {}

    /**
     * Calculate mine probability for every hidden cell, combining exact
     * component enumeration with global mine-count weighting.
     *
     * @param view                  current board (0-8 revealed, 9 unrevealed)
     * @param equations             equations after deterministic inference
     * @param hiddenUnknown         unrevealed cells that are NOT known mines
     * @param remainingUnknownMines total mines minus known hidden mines
     * @param h                     board height
     * @param w                     board width
     * @param maxEnumCells          per-component enum cell limit
     * @param maxValidAssignments   per-component assignment limit
     * @param knownMines            mutable set of known mine keys
     * @param knownSafe             mutable set of known safe keys
     * @return cell-key → mine probability map (may be empty)
     */
    public static Map<Long, Double> calculate(int[][] view, List<Equation> equations,
                                              List<Long> hiddenUnknown, int remainingUnknownMines,
                                              int h, int w,
                                              int maxEnumCells, int maxValidAssignments,
                                              Set<Long> knownMines, Set<Long> knownSafe) {
        List<Component> components = ComponentDiscovery.buildComponents(equations);
        if (components.isEmpty()) return Map.of();

        List<CompStats> compStats = new ArrayList<>();
        Set<Long> failedCompCells = new HashSet<>();
        Set<Long> frontierCells = new HashSet<>();

        for (Component comp : components) {
            int maxMinesForComp = Math.min(comp.cells().size(), remainingUnknownMines);
            CompStats stats = ComponentEnumerator.enumerate(comp.cells(), comp.equations(),
                    maxEnumCells, maxValidAssignments, maxMinesForComp);
            if (stats == null) {
                failedCompCells.addAll(comp.cells());
            } else {
                compStats.add(stats);
                frontierCells.addAll(comp.cells());
            }
        }

        // ---- Partial fallback: some components failed ----
        if (!failedCompCells.isEmpty()) {
            return partialFallback(view, hiddenUnknown, remainingUnknownMines,
                    h, w, compStats, frontierCells, failedCompCells,
                    knownMines, knownSafe);
        }

        // ---- All components succeeded: full global DP weighting ----
        return fullGlobalDP(hiddenUnknown, remainingUnknownMines,
                compStats, frontierCells);
    }

    // ---------- partial fallback path ----------

    private static Map<Long, Double> partialFallback(int[][] view, List<Long> hiddenUnknown,
                                                     int remainingUnknownMines,
                                                     int h, int w,
                                                     List<CompStats> compStats,
                                                     Set<Long> frontierCells,
                                                     Set<Long> failedCompCells,
                                                     Set<Long> knownMines, Set<Long> knownSafe) {
        Map<Long, Double> probs = new HashMap<>();

        // Exact local probabilities for successful components
        for (CompStats stats : compStats) {
            int total = stats.validTotal();
            if (total == 0) continue;
            for (long cell : stats.cells()) {
                long mineCount = 0;
                for (long v : stats.cellByK().get(cell).values()) mineCount += v;
                probs.put(cell, (double) mineCount / total);
            }
        }

        for (var entry : new ArrayList<>(probs.entrySet())) {
            if (entry.getValue() <= 0.0) knownSafe.add(entry.getKey());
            else if (entry.getValue() >= 1.0) knownMines.add(entry.getKey());
        }

        List<Long> failedList = new ArrayList<>();
        for (long c : hiddenUnknown) {
            if (failedCompCells.contains(c)) failedList.add(c);
        }
        if (!failedList.isEmpty()) {
            double exactMines = 0;
            for (long c : frontierCells) exactMines += probs.getOrDefault(c, 0.0);
            double remainingForRest = Math.max(0, remainingUnknownMines - exactMines);
            int restCellsCount = hiddenUnknown.size() - frontierCells.size();
            Map<Long, Double> hProbs = HeuristicProbabilityCalculator.calculate(view, failedList,
                    restCellsCount > 0 ? remainingForRest : remainingUnknownMines,
                    h, w, knownMines, knownSafe);
            probs.putAll(hProbs);
        }

        Set<Long> allConstrained = new HashSet<>(frontierCells);
        allConstrained.addAll(failedCompCells);
        List<Long> unconstrained = new ArrayList<>();
        for (long c : hiddenUnknown) {
            if (!allConstrained.contains(c)) unconstrained.add(c);
        }
        if (!unconstrained.isEmpty()) {
            double estMines = 0;
            for (long c : allConstrained) estMines += probs.getOrDefault(c, 0.0);
            double remainingEst = Math.max(0.0, remainingUnknownMines - estMines);
            double uncProb = Math.max(0.0, Math.min(1.0, remainingEst / unconstrained.size()));
            for (long c : unconstrained) probs.put(c, uncProb);
        }

        return probs;
    }

    // ---------- full global DP path ----------

    private static Map<Long, Double> fullGlobalDP(List<Long> hiddenUnknown,
                                                  int remainingUnknownMines,
                                                  List<CompStats> compStats,
                                                  Set<Long> frontierCells) {
        List<Long> unconstrainedCells = new ArrayList<>();
        for (long c : hiddenUnknown) {
            if (!frontierCells.contains(c)) unconstrainedCells.add(c);
        }
        int unconstrainedCount = unconstrainedCells.size();

        // DP forward (BigInteger to avoid overflow)
        List<Map<Integer, BigInteger>> dp = new ArrayList<>();
        dp.add(new HashMap<>(Map.of(0, BigInteger.ONE)));
        for (CompStats stats : compStats) {
            Map<Integer, BigInteger> prev = dp.getLast();
            Map<Integer, BigInteger> next = new HashMap<>();
            for (var pe : prev.entrySet()) {
                for (var ke : stats.byK().entrySet()) {
                    int newKey = pe.getKey() + ke.getKey();
                    BigInteger product = pe.getValue().multiply(BigInteger.valueOf(ke.getValue()));
                    next.merge(newKey, product, BigInteger::add);
                }
            }
            dp.add(next);
        }

        // Suffix product
        List<Map<Integer, BigInteger>> suffix = new ArrayList<>(Collections.nCopies(compStats.size() + 1, null));
        suffix.set(compStats.size(), new HashMap<>(Map.of(0, BigInteger.ONE)));
        for (int i = compStats.size() - 1; i >= 0; i--) {
            Map<Integer, BigInteger> acc = new HashMap<>();
            for (var k1e : compStats.get(i).byK().entrySet()) {
                for (var k2e : suffix.get(i + 1).entrySet()) {
                    int newKey = k1e.getKey() + k2e.getKey();
                    BigInteger product = BigInteger.valueOf(k1e.getValue()).multiply(k2e.getValue());
                    acc.merge(newKey, product, BigInteger::add);
                }
            }
            suffix.set(i, acc);
        }

        BigInteger totalWeight = BigInteger.ZERO;
        List<Map<Integer, BigInteger>> compMineMass = new ArrayList<>();
        for (int i = 0; i < compStats.size(); i++) compMineMass.add(new HashMap<>());
        Map<Long, BigInteger> cellMineMass = new HashMap<>();
        BigInteger unconstrainedMineMass = BigInteger.ZERO;

        Map<Integer, BigInteger> dpLast = dp.getLast();
        for (var entry : dpLast.entrySet()) {
            int frontierMines = entry.getKey();
            BigInteger waysFrontier = entry.getValue();
            int outsideMines = remainingUnknownMines - frontierMines;
            if (outsideMines < 0 || outsideMines > unconstrainedCount) continue;
            BigInteger outsideWays = comb(unconstrainedCount, outsideMines);
            BigInteger weight = waysFrontier.multiply(outsideWays);
            if (weight.signum() == 0) continue;
            totalWeight = totalWeight.add(weight);
            unconstrainedMineMass = unconstrainedMineMass.add(weight.multiply(BigInteger.valueOf(outsideMines)));

            for (int i = 0; i < compStats.size(); i++) {
                Map<Integer, BigInteger> leftDist = dp.get(i);
                Map<Integer, BigInteger> rightDist = suffix.get(i + 1);
                for (var ki : compStats.get(i).byK().entrySet()) {
                    int kI = ki.getKey();
                    int needLR = frontierMines - kI;
                    BigInteger waysLR = BigInteger.ZERO;
                    for (var le : leftDist.entrySet()) {
                        int kRight = needLR - le.getKey();
                        BigInteger waysRight = rightDist.get(kRight);
                        if (waysRight != null) {
                            waysLR = waysLR.add(le.getValue().multiply(waysRight));
                        }
                    }
                    if (waysLR.signum() != 0) {
                        compMineMass.get(i).merge(kI, waysLR.multiply(outsideWays), BigInteger::add);
                    }
                }
            }
        }

        if (totalWeight.signum() == 0) return Map.of();

        for (int i = 0; i < compStats.size(); i++) {
            CompStats stats = compStats.get(i);
            Map<Integer, BigInteger> weightByK = compMineMass.get(i);
            for (long cell : stats.cells()) {
                BigInteger minesMass = BigInteger.ZERO;
                Map<Integer, Long> cbk = stats.cellByK().get(cell);
                for (var kw : weightByK.entrySet()) {
                    Long cellWays = cbk.get(kw.getKey());
                    if (cellWays != null) {
                        minesMass = minesMass.add(kw.getValue().multiply(BigInteger.valueOf(cellWays)));
                    }
                }
                cellMineMass.put(cell, minesMass);
            }
        }

        Map<Long, Double> probs = new HashMap<>();
        double dTotalWeight = totalWeight.doubleValue();
        for (long cell : frontierCells) {
            BigInteger mass = cellMineMass.getOrDefault(cell, BigInteger.ZERO);
            probs.put(cell, mass.doubleValue() / dTotalWeight);
        }

        if (unconstrainedCount > 0) {
            double uncProb = unconstrainedMineMass.doubleValue() / (dTotalWeight * unconstrainedCount);
            for (long cell : unconstrainedCells) probs.put(cell, uncProb);
        }

        return probs;
    }
}
