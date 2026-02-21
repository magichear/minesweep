package com.magichear.minesweepBackend.model;

import lombok.Getter;

@Getter
public enum Difficulty {
    EASY(9, 9, 10),
    MEDIUM(16, 16, 40),
    HARD(16, 30, 99);

    private final int rows;
    private final int cols;
    private final int mines;

    Difficulty(int rows, int cols, int mines) {
        this.rows = rows;
        this.cols = cols;
        this.mines = mines;
    }
}
