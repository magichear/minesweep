package com.magichear.minesweepBackend.external.dto;

/**
 * Request to perform auto-click at a specific screen position.
 */
public record AutoClickRequest(
        int screenX,
        int screenY,
        boolean rightClick
) {}
