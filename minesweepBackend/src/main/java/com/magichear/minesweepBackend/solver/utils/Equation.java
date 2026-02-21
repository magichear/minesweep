package com.magichear.minesweepBackend.solver.utils;

import java.util.Set;

/**
 * A linear constraint over a set of boundary cells:
 * "exactly {@code mines} of the cells in {@code cells} are mines."
 *
 * @param cells the set of cell-keys involved in this equation
 * @param mines the number of mines among those cells
 */
public record Equation(Set<Long> cells, int mines) {}
