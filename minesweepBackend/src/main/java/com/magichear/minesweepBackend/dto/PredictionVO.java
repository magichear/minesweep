package com.magichear.minesweepBackend.dto;

import lombok.Data;

@Data
public class PredictionVO {
    private double[][] probabilities;
    private int safestRow;
    private int safestCol;
}
