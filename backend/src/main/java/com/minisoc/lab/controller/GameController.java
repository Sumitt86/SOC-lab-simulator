package com.minisoc.lab.controller;

import com.minisoc.lab.service.GameStateService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST API for game state management.
 * Provides endpoints to view game status, start/reset games, and force end conditions.
 */
@RestController
@RequestMapping("/api/game")
@CrossOrigin(origins = "*")
public class GameController {

    private final GameStateService gameStateService;

    public GameController(GameStateService gameStateService) {
        this.gameStateService = gameStateService;
    }

    /**
     * Get current game status.
     * 
     * GET /api/game/status
     * Response: { gameId, status, phase, redScore, blueScore, elapsedSeconds, threatScore, ... }
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getGameStatus() {
        return ResponseEntity.ok(gameStateService.getGameStatus());
    }

    /**
     * Start a new game.
     * 
     * POST /api/game/start
     */
    @PostMapping("/start")
    public ResponseEntity<Map<String, String>> startNewGame() {
        gameStateService.startNewGame();
        return ResponseEntity.ok(Map.of(
            "status", "success",
            "message", "New game started"
        ));
    }

    /**
     * Reset the current game.
     * 
     * POST /api/game/reset
     */
    @PostMapping("/reset")
    public ResponseEntity<Map<String, String>> resetGame() {
        gameStateService.resetGame();
        return ResponseEntity.ok(Map.of(
            "status", "success",
            "message", "Game reset successfully"
        ));
    }

    /**
     * Force end the game (admin/debug).
     * 
     * POST /api/game/end
     * Body: { winner: "RED|BLUE|DRAW", reason: "..." }
     */
    @PostMapping("/end")
    public ResponseEntity<Map<String, String>> forceEndGame(@RequestBody Map<String, String> request) {
        String winner = request.getOrDefault("winner", "DRAW");
        String reason = request.getOrDefault("reason", "Forced end");
        
        gameStateService.forceEndGame(winner, reason);
        
        return ResponseEntity.ok(Map.of(
            "status", "success",
            "message", "Game ended: " + winner + " wins"
        ));
    }
}
