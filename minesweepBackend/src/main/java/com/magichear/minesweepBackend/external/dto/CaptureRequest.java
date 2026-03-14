package com.magichear.minesweepBackend.external.dto;

/**
 * Request to capture a screen region.
 */
public record CaptureRequest(
        int x,
        int y,
        int width,
        int height,
        boolean debugMode
) {}
