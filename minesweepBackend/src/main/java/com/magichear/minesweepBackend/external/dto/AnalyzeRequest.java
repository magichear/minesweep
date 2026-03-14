package com.magichear.minesweepBackend.external.dto;

/**
 * Request to analyze a minesweeper board screenshot.
 */
public record AnalyzeRequest(
        int rows,
        int cols,
        int mines,
        boolean debugMode
) {}
