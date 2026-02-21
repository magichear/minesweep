package com.magichear.minesweepBackend.solver.utils;

import java.util.*;

/**
 * Discovers independent connected components among frontier (boundary) cells.
 * Two cells belong to the same component if they share at least one equation.
 */
public final class ComponentDiscovery {

    private ComponentDiscovery() {}

    /**
     * A connected component: a set of frontier cells together with
     * all equations that are fully contained within those cells.
     */
    public record Component(Set<Long> cells, List<Equation> equations) {}

    /**
     * Partition the frontier cells referenced by {@code equations} into
     * independent connected components.
     */
    public static List<Component> buildComponents(List<Equation> equations) {
        Set<Long> frontierCells = new HashSet<>();
        for (Equation eq : equations) frontierCells.addAll(eq.cells());
        if (frontierCells.isEmpty()) return List.of();

        Map<Long, Set<Long>> adj = new HashMap<>();
        for (long cell : frontierCells) adj.put(cell, new HashSet<>());
        for (Equation eq : equations) {
            List<Long> cells = new ArrayList<>(eq.cells());
            for (int i = 0; i < cells.size(); i++) {
                for (int j = i + 1; j < cells.size(); j++) {
                    adj.get(cells.get(i)).add(cells.get(j));
                    adj.get(cells.get(j)).add(cells.get(i));
                }
            }
        }

        List<Component> components = new ArrayList<>();
        Set<Long> visited = new HashSet<>();
        for (long cell : frontierCells) {
            if (visited.contains(cell)) continue;
            Set<Long> comp = new HashSet<>();
            Deque<Long> queue = new ArrayDeque<>();
            queue.add(cell);
            visited.add(cell);
            while (!queue.isEmpty()) {
                long cur = queue.poll();
                comp.add(cur);
                for (long nxt : adj.get(cur)) {
                    if (visited.add(nxt)) queue.add(nxt);
                }
            }
            List<Equation> compEqs = new ArrayList<>();
            for (Equation eq : equations) {
                if (comp.containsAll(eq.cells())) compEqs.add(eq);
            }
            components.add(new Component(comp, compEqs));
        }
        return components;
    }
}
