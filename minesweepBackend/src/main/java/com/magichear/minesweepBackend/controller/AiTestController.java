package com.magichear.minesweepBackend.controller;

import com.magichear.minesweepBackend.dto.AiTestRequest;
import com.magichear.minesweepBackend.dto.AiTestStatusVO;
import com.magichear.minesweepBackend.dto.AiTestVO;
import com.magichear.minesweepBackend.service.SolverTestService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/ai-test")
public class AiTestController {

    private final SolverTestService solverTestService;

    public AiTestController(SolverTestService solverTestService) {
        this.solverTestService = solverTestService;
    }

    @PostMapping("/start")
    public ResponseEntity<AiTestStatusVO> startTest(@RequestBody AiTestRequest request,
                                                     HttpServletRequest httpRequest) {
        String username = (String) httpRequest.getAttribute("currentUser");
        log.info("POST /api/ai-test/start – testName={}, difficulty={}, gameRule={}, user={}",
                request.getTestName(), request.getDifficulty(), request.getGameRule(), username);
        AiTestStatusVO status = solverTestService.startTest(
                request.getTestName(), request.getDifficulty(), request.getGameRule(), username);
        return ResponseEntity.ok(status);
    }

    @GetMapping("/status/{trackingId}")
    public ResponseEntity<AiTestStatusVO> getStatus(@PathVariable String trackingId) {
        return ResponseEntity.ok(solverTestService.getStatus(trackingId));
    }

    @GetMapping
    public ResponseEntity<List<AiTestVO>> getAllTests() {
        return ResponseEntity.ok(solverTestService.getAllTests());
    }
}
