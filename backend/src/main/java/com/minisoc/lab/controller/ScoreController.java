package com.minisoc.lab.controller;

import com.minisoc.lab.model.GameEvent;
import com.minisoc.lab.model.GameState;
import com.minisoc.lab.service.ScoringService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST API for game scoring system.
 * Provides endpoints to view scores, events, and scoring statistics.
 */
@RestController
@RequestMapping("/api/score")
@CrossOrigin(origins = "*")
public class ScoreController {

    private final ScoringService scoringService;
    private final GameState gameState; // Singleton game state

    public ScoreController(ScoringService scoringService, GameState gameState) {
        this.scoringService = scoringService;
        this.gameState = gameState;
    }

    /**
     * Get current scores for both teams.
     * 
     * GET /api/score/current
     * Response: { redScore, blueScore, scoreDifference, elapsedSeconds }
     */
    @GetMapping("/current")
    public ResponseEntity<Map<String, Object>> getCurrentScores() {
        return ResponseEntity.ok(Map.of(
            "redScore", gameState.getRedScore(),
            "blueScore", gameState.getBlueScore(),
            "scoreDifference", gameState.getRedScore() - gameState.getBlueScore(),
            "elapsedSeconds", gameState.getElapsedSeconds(),
            "gameStatus", gameState.getStatus().toString()
        ));
    }

    /**
     * Get detailed scoring summary including event counts.
     * 
     * GET /api/score/summary
     * Response: { gameId, redScore, blueScore, scoreDifference, redEvents, blueEvents, totalEvents }
     */
    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getScoringSummary() {
        return ResponseEntity.ok(scoringService.getScoringSummary(gameState));
    }

    /**
     * Get all scoring events for the current game.
     * 
     * GET /api/score/events
     * Response: [ { eventId, type, team, points, mitreId, description, timestamp }, ... ]
     */
    @GetMapping("/events")
    public ResponseEntity<List<GameEvent>> getGameEvents() {
        List<GameEvent> events = scoringService.getGameEvents(gameState.getGameId());
        return ResponseEntity.ok(events);
    }

    /**
     * Get scoring events filtered by team.
     * 
     * GET /api/score/events/red
     * GET /api/score/events/blue
     */
    @GetMapping("/events/{team}")
    public ResponseEntity<List<GameEvent>> getGameEventsByTeam(@PathVariable String team) {
        List<GameEvent> events = scoringService.getGameEvents(gameState.getGameId())
            .stream()
            .filter(e -> team.equalsIgnoreCase(e.getTeam()))
            .toList();
        return ResponseEntity.ok(events);
    }

    /**
     * Reset scores (for new game).
     * 
     * POST /api/score/reset
     */
    @PostMapping("/reset")
    public ResponseEntity<Map<String, String>> resetScores() {
        gameState.setRedScore(0);
        gameState.setBlueScore(0);
        scoringService.clearGameEvents(gameState.getGameId());
        
        return ResponseEntity.ok(Map.of(
            "status", "success",
            "message", "Scores reset successfully"
        ));
    }

    /**
     * Get scoring breakdown by event type.
     * 
     * GET /api/score/breakdown
     * Response: { RED: { INITIAL_ACCESS: 50, PERSISTENCE: 75, ... }, BLUE: { ... } }
     */
    @GetMapping("/breakdown")
    public ResponseEntity<Map<String, Map<String, Integer>>> getScoreBreakdown() {
        List<GameEvent> events = scoringService.getGameEvents(gameState.getGameId());
        
        Map<String, Map<String, Integer>> breakdown = Map.of(
            "RED", new java.util.HashMap<>(),
            "BLUE", new java.util.HashMap<>()
        );
        
        for (GameEvent event : events) {
            Map<String, Integer> teamMap = breakdown.get(event.getTeam());
            if (teamMap != null) {
                String eventType = event.getType().toString();
                teamMap.put(eventType, teamMap.getOrDefault(eventType, 0) + event.getPoints());
            }
        }
        
        return ResponseEntity.ok(breakdown);
    }

    /**
     * Get leaderboard stats (for future multi-game support).
     * 
     * GET /api/score/leaderboard
     * Response: [ { gameId, redScore, blueScore, winner, duration }, ... ]
     */
    @GetMapping("/leaderboard")
    public ResponseEntity<List<Map<String, Object>>> getLeaderboard() {
        // For now, return current game stats
        // In production, this would query a database of completed games
        Map<String, Object> currentGame = Map.of(
            "gameId", gameState.getGameId(),
            "redScore", gameState.getRedScore(),
            "blueScore", gameState.getBlueScore(),
            "winner", determineWinner(),
            "duration", gameState.getElapsedSeconds(),
            "status", gameState.getStatus().toString()
        );
        
        return ResponseEntity.ok(List.of(currentGame));
    }

    /**
     * Helper to determine winner based on current scores.
     */
    private String determineWinner() {
        int red = gameState.getRedScore();
        int blue = gameState.getBlueScore();
        
        if (red > blue) return "RED";
        else if (blue > red) return "BLUE";
        else return "DRAW";
    }
}
