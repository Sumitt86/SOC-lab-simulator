package com.minisoc.lab.service;

import com.minisoc.lab.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages scoring logic for red and blue teams.
 * Awards points based on attack progression and detection/remediation actions.
 */
@Service
public class ScoringService {

    private static final Logger log = LoggerFactory.getLogger(ScoringService.class);

    private final StreamingService streamingService;
    private final Map<String, List<GameEvent>> gameEvents = new ConcurrentHashMap<>();

    // ──── Point Values ────────────────────────────────────────────────────────
    
    // Red Team Points (Attack Stages)
    private static final int POINTS_RECON = 10;
    private static final int POINTS_INITIAL_ACCESS = 50;
    private static final int POINTS_PERSISTENCE = 75;
    private static final int POINTS_PRIVILEGE_ESCALATION = 100;
    private static final int POINTS_LATERAL_MOVEMENT = 50;
    private static final int POINTS_EXFILTRATION = 200;
    private static final int POINTS_SPEED_BONUS_RED = 50; // Complete in < 5 min
    
    // Blue Team Points (Detection & Response)
    private static final int POINTS_EARLY_DETECTION = 150; // Blocked at initial access
    private static final int POINTS_DETECTION_ALERT = 25;
    private static final int POINTS_PERSISTENCE_BLOCKED = 100;
    private static final int POINTS_PRIVILEGE_ESC_BLOCKED = 125;
    private static final int POINTS_EXFILTRATION_BLOCKED = 150;
    private static final int POINTS_REMEDIATION_SUCCESS = 30;
    private static final int POINTS_IP_BLOCKED = 20;
    private static final int POINTS_SPEED_BONUS_BLUE = 50; // Detect < 2 min
    
    // Penalties
    private static final int PENALTY_DETECTION = -25; // Red penalty when detected
    private static final int PENALTY_MISSED_DETECTION = -10; // Blue penalty for missed critical
    private static final int PENALTY_FALSE_POSITIVE = -5; // Blue penalty for false alarm

    // ──── Constructor ─────────────────────────────────────────────────────────

    public ScoringService(StreamingService streamingService) {
        this.streamingService = streamingService;
    }

    // ──── Red Team Scoring ────────────────────────────────────────────────────

    /**
     * Award points to red team for reconnaissance completion.
     */
    public void awardReconPoints(GameState state, String vectorId, String mitreId) {
        GameEvent event = new GameEvent(
            state.getGameId(),
            GameEvent.EventType.RECON_COMPLETE,
            "RED",
            POINTS_RECON,
            mitreId,
            vectorId,
            "Reconnaissance stage completed"
        );
        recordEvent(state, event);
        state.setRedScore(state.getRedScore() + POINTS_RECON);
        log.info("[SCORE] Red +{} pts: Recon ({})", POINTS_RECON, mitreId);
    }

    /**
     * Award points for gaining initial access.
     */
    public void awardInitialAccessPoints(GameState state, String vectorId, String mitreId) {
        GameEvent event = new GameEvent(
            state.getGameId(),
            GameEvent.EventType.INITIAL_ACCESS,
            "RED",
            POINTS_INITIAL_ACCESS,
            mitreId,
            vectorId,
            "Initial access gained via " + vectorId
        );
        recordEvent(state, event);
        state.setRedScore(state.getRedScore() + POINTS_INITIAL_ACCESS);
        log.info("[SCORE] Red +{} pts: Initial Access ({})", POINTS_INITIAL_ACCESS, mitreId);
    }

    /**
     * Award points for establishing persistence.
     */
    public void awardPersistencePoints(GameState state, String vectorId, String mitreId) {
        GameEvent event = new GameEvent(
            state.getGameId(),
            GameEvent.EventType.PERSISTENCE_ESTABLISHED,
            "RED",
            POINTS_PERSISTENCE,
            mitreId,
            vectorId,
            "Persistence established"
        );
        recordEvent(state, event);
        state.setRedScore(state.getRedScore() + POINTS_PERSISTENCE);
        log.info("[SCORE] Red +{} pts: Persistence ({})", POINTS_PERSISTENCE, mitreId);
    }

    /**
     * Award points for privilege escalation.
     */
    public void awardPrivilegeEscalationPoints(GameState state, String vectorId, String mitreId) {
        GameEvent event = new GameEvent(
            state.getGameId(),
            GameEvent.EventType.PRIVILEGE_ESCALATION,
            "RED",
            POINTS_PRIVILEGE_ESCALATION,
            mitreId,
            vectorId,
            "Privilege escalation achieved"
        );
        recordEvent(state, event);
        state.setRedScore(state.getRedScore() + POINTS_PRIVILEGE_ESCALATION);
        log.info("[SCORE] Red +{} pts: Priv Esc ({})", POINTS_PRIVILEGE_ESCALATION, mitreId);
    }

    /**
     * Award points for data exfiltration.
     */
    public void awardExfiltrationPoints(GameState state, String vectorId, String mitreId) {
        GameEvent event = new GameEvent(
            state.getGameId(),
            GameEvent.EventType.EXFILTRATION,
            "RED",
            POINTS_EXFILTRATION,
            mitreId,
            vectorId,
            "Data exfiltration completed"
        );
        recordEvent(state, event);
        state.setRedScore(state.getRedScore() + POINTS_EXFILTRATION);
        log.info("[SCORE] Red +{} pts: Exfiltration ({})", POINTS_EXFILTRATION, mitreId);
    }

    /**
     * Award speed bonus to red team if attack completes in < 5 minutes.
     */
    public void checkRedSpeedBonus(GameState state, String vectorId) {
        long elapsedSeconds = state.getElapsedSeconds();
        if (elapsedSeconds < 300) { // 5 minutes
            GameEvent event = new GameEvent(
                state.getGameId(),
                GameEvent.EventType.SPEED_BONUS_RED,
                "RED",
                POINTS_SPEED_BONUS_RED,
                null,
                vectorId,
                "Speed bonus: Completed in " + elapsedSeconds + " seconds"
            );
            recordEvent(state, event);
            state.setRedScore(state.getRedScore() + POINTS_SPEED_BONUS_RED);
            log.info("[SCORE] Red +{} pts: Speed Bonus", POINTS_SPEED_BONUS_RED);
        }
    }

    // ──── Blue Team Scoring ───────────────────────────────────────────────────

    /**
     * Award points for detection alert generation.
     */
    public void awardDetectionPoints(GameState state, String mitreId, String vectorId) {
        GameEvent event = new GameEvent(
            state.getGameId(),
            GameEvent.EventType.DETECTION_ALERT,
            "BLUE",
            POINTS_DETECTION_ALERT,
            mitreId,
            vectorId,
            "Detection alert triggered for " + mitreId
        );
        recordEvent(state, event);
        state.setBlueScore(state.getBlueScore() + POINTS_DETECTION_ALERT);
        log.info("[SCORE] Blue +{} pts: Detection ({})", POINTS_DETECTION_ALERT, mitreId);
    }

    /**
     * Award high points for blocking initial access (early detection).
     */
    public void awardEarlyDetectionPoints(GameState state, String mitreId, String vectorId) {
        GameEvent event = new GameEvent(
            state.getGameId(),
            GameEvent.EventType.EARLY_DETECTION,
            "BLUE",
            POINTS_EARLY_DETECTION,
            mitreId,
            vectorId,
            "Early detection: Blocked at initial access stage"
        );
        recordEvent(state, event);
        state.setBlueScore(state.getBlueScore() + POINTS_EARLY_DETECTION);
        log.info("[SCORE] Blue +{} pts: Early Detection ({})", POINTS_EARLY_DETECTION, mitreId);
        
        // Also apply penalty to red team
        applyDetectionPenalty(state, vectorId);
    }

    /**
     * Award points for blocking persistence.
     */
    public void awardPersistenceBlockedPoints(GameState state, String mitreId, String vectorId) {
        GameEvent event = new GameEvent(
            state.getGameId(),
            GameEvent.EventType.PERSISTENCE_BLOCKED,
            "BLUE",
            POINTS_PERSISTENCE_BLOCKED,
            mitreId,
            vectorId,
            "Persistence attempt blocked"
        );
        recordEvent(state, event);
        state.setBlueScore(state.getBlueScore() + POINTS_PERSISTENCE_BLOCKED);
        log.info("[SCORE] Blue +{} pts: Persistence Blocked", POINTS_PERSISTENCE_BLOCKED);
        
        applyDetectionPenalty(state, vectorId);
    }

    /**
     * Award points for blocking privilege escalation.
     */
    public void awardPrivEscBlockedPoints(GameState state, String mitreId, String vectorId) {
        GameEvent event = new GameEvent(
            state.getGameId(),
            GameEvent.EventType.PRIVILEGE_ESC_BLOCKED,
            "BLUE",
            POINTS_PRIVILEGE_ESC_BLOCKED,
            mitreId,
            vectorId,
            "Privilege escalation blocked"
        );
        recordEvent(state, event);
        state.setBlueScore(state.getBlueScore() + POINTS_PRIVILEGE_ESC_BLOCKED);
        log.info("[SCORE] Blue +{} pts: Priv Esc Blocked", POINTS_PRIVILEGE_ESC_BLOCKED);
        
        applyDetectionPenalty(state, vectorId);
    }

    /**
     * Award points for blocking exfiltration.
     */
    public void awardExfiltrationBlockedPoints(GameState state, String mitreId, String vectorId) {
        GameEvent event = new GameEvent(
            state.getGameId(),
            GameEvent.EventType.EXFILTRATION_BLOCKED,
            "BLUE",
            POINTS_EXFILTRATION_BLOCKED,
            mitreId,
            vectorId,
            "Data exfiltration blocked"
        );
        recordEvent(state, event);
        state.setBlueScore(state.getBlueScore() + POINTS_EXFILTRATION_BLOCKED);
        log.info("[SCORE] Blue +{} pts: Exfiltration Blocked", POINTS_EXFILTRATION_BLOCKED);
    }

    /**
     * Award points for successful remediation action.
     */
    public void awardRemediationPoints(GameState state, String mitreId, String action) {
        GameEvent event = new GameEvent(
            state.getGameId(),
            GameEvent.EventType.REMEDIATION_SUCCESS,
            "BLUE",
            POINTS_REMEDIATION_SUCCESS,
            mitreId,
            null,
            "Remediation action: " + action
        );
        recordEvent(state, event);
        state.setBlueScore(state.getBlueScore() + POINTS_REMEDIATION_SUCCESS);
        log.info("[SCORE] Blue +{} pts: Remediation ({})", POINTS_REMEDIATION_SUCCESS, action);
    }

    /**
     * Award points for blocking an IP address.
     */
    public void awardIpBlockedPoints(GameState state, String ipAddress) {
        GameEvent event = new GameEvent(
            state.getGameId(),
            GameEvent.EventType.IP_BLOCKED,
            "BLUE",
            POINTS_IP_BLOCKED,
            null,
            null,
            "IP address blocked: " + ipAddress
        );
        recordEvent(state, event);
        state.setBlueScore(state.getBlueScore() + POINTS_IP_BLOCKED);
        log.info("[SCORE] Blue +{} pts: IP Blocked ({})", POINTS_IP_BLOCKED, ipAddress);
    }

    /**
     * Award speed bonus to blue team if first detection occurs in < 2 minutes.
     */
    public void checkBlueSpeedBonus(GameState state, String mitreId) {
        long elapsedSeconds = state.getElapsedSeconds();
        if (elapsedSeconds < 120) { // 2 minutes
            GameEvent event = new GameEvent(
                state.getGameId(),
                GameEvent.EventType.SPEED_BONUS_BLUE,
                "BLUE",
                POINTS_SPEED_BONUS_BLUE,
                mitreId,
                null,
                "Speed bonus: First detection in " + elapsedSeconds + " seconds"
            );
            recordEvent(state, event);
            state.setBlueScore(state.getBlueScore() + POINTS_SPEED_BONUS_BLUE);
            log.info("[SCORE] Blue +{} pts: Speed Bonus", POINTS_SPEED_BONUS_BLUE);
        }
    }

    // ──── Penalties ───────────────────────────────────────────────────────────

    /**
     * Apply penalty to red team when detected.
     */
    private void applyDetectionPenalty(GameState state, String vectorId) {
        GameEvent event = new GameEvent(
            state.getGameId(),
            GameEvent.EventType.DETECTION_PENALTY_RED,
            "RED",
            PENALTY_DETECTION,
            null,
            vectorId,
            "Penalty: Attack detected"
        );
        recordEvent(state, event);
        state.setRedScore(state.getRedScore() + PENALTY_DETECTION);
        log.info("[SCORE] Red {} pts: Detection Penalty", PENALTY_DETECTION);
    }

    /**
     * Apply penalty to blue team for missing critical detection.
     */
    public void applyMissedDetectionPenalty(GameState state, String mitreId) {
        GameEvent event = new GameEvent(
            state.getGameId(),
            GameEvent.EventType.MISSED_DETECTION_BLUE,
            "BLUE",
            PENALTY_MISSED_DETECTION,
            mitreId,
            null,
            "Penalty: Missed critical detection for " + mitreId
        );
        recordEvent(state, event);
        state.setBlueScore(state.getBlueScore() + PENALTY_MISSED_DETECTION);
        log.info("[SCORE] Blue {} pts: Missed Detection", PENALTY_MISSED_DETECTION);
    }

    /**
     * Apply penalty for false positive alert.
     */
    public void applyFalsePositivePenalty(GameState state, String alertId) {
        GameEvent event = new GameEvent(
            state.getGameId(),
            GameEvent.EventType.FALSE_POSITIVE_BLUE,
            "BLUE",
            PENALTY_FALSE_POSITIVE,
            null,
            null,
            "Penalty: False positive alert " + alertId
        );
        recordEvent(state, event);
        state.setBlueScore(state.getBlueScore() + PENALTY_FALSE_POSITIVE);
        log.info("[SCORE] Blue {} pts: False Positive", PENALTY_FALSE_POSITIVE);
    }

    // ──── Event Management ────────────────────────────────────────────────────

    /**
     * Record a game event and push to frontend via SSE.
     */
    private void recordEvent(GameState state, GameEvent event) {
        gameEvents.computeIfAbsent(state.getGameId(), k -> new ArrayList<>()).add(event);
        
        // Push score update to frontend via SSE
        LogEntry scoreLog = new LogEntry(
            System.currentTimeMillis(),
            event.getTimestamp().toString(),
            event.getPoints() > 0 ? "INFO" : "WARN",
            String.format("[%s] %s: %+d pts - %s", 
                event.getTeam(), 
                event.getType(), 
                event.getPoints(), 
                event.getDescription())
        );
        streamingService.pushLog(scoreLog);
    }

    /**
     * Get all events for a game.
     */
    public List<GameEvent> getGameEvents(String gameId) {
        return new ArrayList<>(gameEvents.getOrDefault(gameId, new ArrayList<>()));
    }

    /**
     * Clear all events for a game (when resetting).
     */
    public void clearGameEvents(String gameId) {
        gameEvents.remove(gameId);
    }

    /**
     * Get scoring summary for display.
     */
    public Map<String, Object> getScoringSummary(GameState state) {
        List<GameEvent> events = getGameEvents(state.getGameId());
        
        long redEvents = events.stream().filter(e -> "RED".equals(e.getTeam())).count();
        long blueEvents = events.stream().filter(e -> "BLUE".equals(e.getTeam())).count();
        
        return Map.of(
            "gameId", state.getGameId(),
            "redScore", state.getRedScore(),
            "blueScore", state.getBlueScore(),
            "scoreDifference", state.getRedScore() - state.getBlueScore(),
            "redEvents", redEvents,
            "blueEvents", blueEvents,
            "totalEvents", events.size(),
            "elapsedSeconds", state.getElapsedSeconds()
        );
    }
}
