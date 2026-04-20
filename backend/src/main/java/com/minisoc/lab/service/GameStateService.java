package com.minisoc.lab.service;

import com.minisoc.lab.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Manages game state transitions and end conditions.
 * Handles game lifecycle: SETUP → ACTIVE → DETECTION → END
 */
@Service
public class GameStateService {

    private static final Logger log = LoggerFactory.getLogger(GameStateService.class);

    private final GameState gameState;
    private final ScoringService scoringService;
    private final StreamingService streamingService;

    // Game constants
    private static final long GAME_DURATION_SECONDS = 1800; // 30 minutes
    private static final int EXFILTRATION_WIN_THRESHOLD = 200;

    public GameStateService(GameState gameState, 
                           ScoringService scoringService,
                           StreamingService streamingService) {
        this.gameState = gameState;
        this.scoringService = scoringService;
        this.streamingService = streamingService;
    }

    /**
     * Initialize a new game session.
     */
    public void startNewGame() {
        gameState.setStatus(GameStatus.ACTIVE);
        gameState.setPhase(AttackPhase.INITIAL_ACCESS);
        gameState.setRedScore(0);
        gameState.setBlueScore(0);
        gameState.setStartTimeMillis(System.currentTimeMillis());
        gameState.setPersistenceActive(false);
        gameState.setBeaconCount(0);
        gameState.getBlockedIPs().clear();
        gameState.getIsolatedHosts().clear();
        scoringService.clearGameEvents(gameState.getGameId());
        
        log.info("[GAME] New game started: {}", gameState.getGameId());
        
        LogEntry startLog = new LogEntry(
            System.currentTimeMillis(),
            java.time.Instant.now().toString(),
            "INFO",
            "🎮 New game started - Red vs Blue team competition begins!"
        );
        streamingService.pushLog(startLog);
    }

    /**
     * Progress to next attack phase.
     */
    public void advancePhase(AttackPhase nextPhase) {
        AttackPhase previousPhase = gameState.getPhase();
        gameState.setPhase(nextPhase);
        
        log.info("[GAME] Phase transition: {} → {}", previousPhase, nextPhase);
        
        LogEntry phaseLog = new LogEntry(
            System.currentTimeMillis(),
            java.time.Instant.now().toString(),
            "WARN",
            String.format("⚠️ Attack phase advanced: %s → %s", previousPhase, nextPhase)
        );
        streamingService.pushLog(phaseLog);
        
        // Check if we should end the game based on phase
        checkEndConditions();
    }

    /**
     * Check if game should end and determine winner.
     */
    public void checkEndConditions() {
        if (gameState.getStatus() != GameStatus.ACTIVE) {
            return; // Game already ended
        }

        // Check time limit
        if (gameState.getElapsedSeconds() >= GAME_DURATION_SECONDS) {
            endGameDraw("Time limit reached (30 minutes)");
            return;
        }

        // RED WINS: If exfiltration completes with high score
        if (gameState.getPhase() == AttackPhase.EXFILTRATION && 
            gameState.getRedScore() >= EXFILTRATION_WIN_THRESHOLD) {
            endGameRedWin("Red team successfully exfiltrated data");
            return;
        }

        // BLUE WINS: If all attack vectors are blocked
        int blockedCount = gameState.getBlockedIPs().size();
        if (blockedCount >= 3 && gameState.getBlueScore() > gameState.getRedScore()) {
            endGameBlueWin("Blue team blocked all attack vectors");
            return;
        }

        // Check threat score for auto-escalation
        int threat = gameState.computeThreatScore();
        if (threat >= 80) {
            LogEntry threatLog = new LogEntry(
                System.currentTimeMillis(),
                java.time.Instant.now().toString(),
                "CRITICAL",
                "🚨 CRITICAL THREAT LEVEL: System compromise imminent!"
            );
            streamingService.pushLog(threatLog);
        }
    }

    /**
     * End game with red team victory.
     */
    private void endGameRedWin(String reason) {
        gameState.setStatus(GameStatus.RED_WIN);
        gameState.setWinReason(reason);
        
        log.info("[GAME] RED WINS: {}", reason);
        
        LogEntry winLog = new LogEntry(
            System.currentTimeMillis(),
            java.time.Instant.now().toString(),
            "CRITICAL",
            "🔴 RED TEAM WINS! " + reason
        );
        streamingService.pushLog(winLog);
        
        // Push final score summary
        pushGameSummary();
    }

    /**
     * End game with blue team victory.
     */
    private void endGameBlueWin(String reason) {
        gameState.setStatus(GameStatus.BLUE_WIN);
        gameState.setWinReason(reason);
        
        log.info("[GAME] BLUE WINS: {}", reason);
        
        LogEntry winLog = new LogEntry(
            System.currentTimeMillis(),
            java.time.Instant.now().toString(),
            "INFO",
            "🔵 BLUE TEAM WINS! " + reason
        );
        streamingService.pushLog(winLog);
        
        pushGameSummary();
    }

    /**
     * End game with draw.
     */
    private void endGameDraw(String reason) {
        gameState.setStatus(GameStatus.DRAW);
        gameState.setWinReason(reason);
        
        log.info("[GAME] DRAW: {}", reason);
        
        LogEntry drawLog = new LogEntry(
            System.currentTimeMillis(),
            java.time.Instant.now().toString(),
            "WARN",
            "⚖️ DRAW! " + reason
        );
        streamingService.pushLog(drawLog);
        
        pushGameSummary();
    }

    /**
     * Push final game summary to frontend.
     */
    private void pushGameSummary() {
        Map<String, Object> summary = scoringService.getScoringSummary(gameState);
        
        LogEntry summaryLog = new LogEntry(
            System.currentTimeMillis(),
            java.time.Instant.now().toString(),
            "INFO",
            String.format("📊 Final Score - RED: %d | BLUE: %d | Duration: %ds",
                gameState.getRedScore(),
                gameState.getBlueScore(),
                gameState.getElapsedSeconds())
        );
        streamingService.pushLog(summaryLog);
    }

    /**
     * Reset game to initial state for rematch.
     */
    public void resetGame() {
        startNewGame();
        log.info("[GAME] Game reset - ready for rematch");
    }

    /**
     * Get current game status.
     */
    public Map<String, Object> getGameStatus() {
        Map<String, Object> status = new java.util.HashMap<>();
        status.put("gameId", gameState.getGameId());
        status.put("status", gameState.getStatus().toString());
        status.put("phase", gameState.getPhase().toString());
        status.put("redScore", gameState.getRedScore());
        status.put("blueScore", gameState.getBlueScore());
        status.put("elapsedSeconds", gameState.getElapsedSeconds());
        status.put("threatScore", gameState.computeThreatScore());
        status.put("threatLevel", gameState.getThreatLevel());
        status.put("persistenceActive", gameState.isPersistenceActive());
        status.put("beaconCount", gameState.getBeaconCount());
        status.put("blockedIPs", gameState.getBlockedIPs().size());
        status.put("winReason", gameState.getWinReason());
        return status;
    }

    /**
     * Force end game (admin/debug).
     */
    public void forceEndGame(String winner, String reason) {
        switch (winner.toUpperCase()) {
            case "RED" -> endGameRedWin(reason);
            case "BLUE" -> endGameBlueWin(reason);
            default -> endGameDraw(reason);
        }
    }
}

