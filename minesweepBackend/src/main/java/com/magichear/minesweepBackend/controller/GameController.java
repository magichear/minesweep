package com.magichear.minesweepBackend.controller;

import com.magichear.minesweepBackend.dto.*;
import com.magichear.minesweepBackend.service.GameService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/game")
public class GameController {

    private final GameService gameService;

    public GameController(GameService gameService) {
        this.gameService = gameService;
    }

    @PostMapping("/new")
    public ResponseEntity<GameStateVO> newGame(@RequestBody NewGameRequest request) {
        log.info("POST /api/game/new – difficulty={}, gameRule={}", request.getDifficulty(), request.getGameRule());
        return ResponseEntity.ok(gameService.createGame(request.getDifficulty(), request.getGameRule()));
    }

    @PostMapping("/{gameId}/reveal")
    public ResponseEntity<GameStateVO> reveal(@PathVariable String gameId,
                                               @RequestBody CellActionRequest request) {
        log.info("POST /api/game/{}/reveal – ({},{})", gameId, request.getRow(), request.getCol());
        return ResponseEntity.ok(
                gameService.revealCell(gameId, request.getRow(), request.getCol()));
    }

    @PostMapping("/{gameId}/flag")
    public ResponseEntity<GameStateVO> flag(@PathVariable String gameId,
                                             @RequestBody CellActionRequest request) {
        log.info("POST /api/game/{}/flag – ({},{})", gameId, request.getRow(), request.getCol());
        return ResponseEntity.ok(
                gameService.toggleFlag(gameId, request.getRow(), request.getCol()));
    }

    @PostMapping("/{gameId}/predict")
    public ResponseEntity<PredictionVO> predict(@PathVariable String gameId) {
        log.info("POST /api/game/{}/predict", gameId);
        return ResponseEntity.ok(gameService.predict(gameId));
    }

    @GetMapping("/{gameId}")
    public ResponseEntity<GameStateVO> getState(@PathVariable String gameId) {
        return ResponseEntity.ok(gameService.getGameState(gameId));
    }
}
