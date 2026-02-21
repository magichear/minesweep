package com.magichear.minesweepBackend.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Centralised exception handling for all REST controllers.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException e) {
        log.warn("Bad request: {}", e.getMessage());
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(NoSuchElementException e) {
        log.warn("Not found: {}", e.getMessage());
        return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleConflict(IllegalStateException e) {
        log.warn("Illegal state: {}", e.getMessage());
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(UnsupportedOperationException.class)
    public ResponseEntity<Map<String, String>> handleUnsupported(UnsupportedOperationException e) {
        log.warn("Unsupported operation: {}", e.getMessage());
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handleRuntime(RuntimeException e) {
        log.error("Internal error: {}", e.getMessage(), e);
        return ResponseEntity.internalServerError()
                .body(Map.of("error", e.getMessage() != null ? e.getMessage() : "Internal server error"));
    }
}
