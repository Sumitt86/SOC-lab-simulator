package com.minisoc.lab.model;

import java.time.Instant;

/**
 * Represents a scoring event in the game (attack, detection, block, remediation).
 * Used for detailed timeline tracking and analytics.
 */
public class GameEvent {

    public enum EventType {
        // Red team events
        RECON_COMPLETE,
        INITIAL_ACCESS,
        PERSISTENCE_ESTABLISHED,
        PRIVILEGE_ESCALATION,
        LATERAL_MOVEMENT,
        EXFILTRATION,
        
        // Blue team events
        DETECTION_ALERT,
        EARLY_DETECTION,
        PERSISTENCE_BLOCKED,
        PRIVILEGE_ESC_BLOCKED,
        EXFILTRATION_BLOCKED,
        REMEDIATION_SUCCESS,
        IP_BLOCKED,
        
        // Time bonuses
        SPEED_BONUS_RED,
        SPEED_BONUS_BLUE,
        
        // Penalties
        DETECTION_PENALTY_RED,
        MISSED_DETECTION_BLUE,
        FALSE_POSITIVE_BLUE
    }

    private final String eventId;
    private final String gameId;
    private final EventType type;
    private final String team; // "RED" or "BLUE"
    private final int points;
    private final String mitreId;
    private final String vectorId;
    private final String description;
    private final Instant timestamp;

    public GameEvent(String gameId, EventType type, String team, int points, 
                     String mitreId, String vectorId, String description) {
        this.eventId = java.util.UUID.randomUUID().toString().substring(0, 8);
        this.gameId = gameId;
        this.type = type;
        this.team = team;
        this.points = points;
        this.mitreId = mitreId;
        this.vectorId = vectorId;
        this.description = description;
        this.timestamp = Instant.now();
    }

    // Getters
    public String getEventId() { return eventId; }
    public String getGameId() { return gameId; }
    public EventType getType() { return type; }
    public String getTeam() { return team; }
    public int getPoints() { return points; }
    public String getMitreId() { return mitreId; }
    public String getVectorId() { return vectorId; }
    public String getDescription() { return description; }
    public Instant getTimestamp() { return timestamp; }
}
