package com.magichear.minesweepBackend.controller;

import com.magichear.minesweepBackend.dto.StatsVO;
import com.magichear.minesweepBackend.service.StatsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/stats")
public class StatsController {

    private final StatsService statsService;

    public StatsController(StatsService statsService) {
        this.statsService = statsService;
    }

    @GetMapping
    public ResponseEntity<StatsVO> getStats() {
        log.info("GET /api/stats");
        return ResponseEntity.ok(statsService.getStats());
    }
}
