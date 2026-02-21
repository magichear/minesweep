package com.magichear.minesweepBackend.dto;

import lombok.Data;

@Data
public class CellActionRequest {
    private int row;
    private int col;
}
