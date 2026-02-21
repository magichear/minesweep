package com.magichear.minesweepBackend.model;

import lombok.Getter;

/**
 * Game rule variants for minesweeper board generation.
 */
@Getter
public enum GameRule {

    /**
     * Safe-zone rule (default): the first-click cell and all 8 neighbours
     * are guaranteed mine-free, so the first click always reveals a blank (0) cell
     * and triggers a cascade.
     */
    SAFE_ZONE("安全区域模式"),

    /**
     * Single-cell-safe rule: only the first-click cell itself is guaranteed safe
     * (a mine is relocated if hit). The first click may reveal a numbered cell.
     * This mirrors the Python {@code game.py} used in solver training/evaluation.
     */
    SINGLE_CELL_SAFE("单格安全模式");

    private final String displayName;

    GameRule(String displayName) {
        this.displayName = displayName;
    }
}
