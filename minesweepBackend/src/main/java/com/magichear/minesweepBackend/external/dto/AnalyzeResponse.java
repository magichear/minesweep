package com.magichear.minesweepBackend.external.dto;

import java.util.List;

/**
 * Response from board analysis: recognized board + solver predictions.
 */
public record AnalyzeResponse(
        int rows,
        int cols,
        int[][] board,
        double[][] probabilities,
        int safestRow,
        int safestCol,
        String action,
        List<String> warnings,
        boolean calibrationOk
) {}
