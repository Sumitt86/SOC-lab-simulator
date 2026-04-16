package com.minisoc.lab.model;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * A processed, actionable alert derived from one or more SystemEvents.
 * This is what the Blue Team sees and acts on.
 */
public class GameAlert {

    public enum AlertStatus {
        OPEN,
        RESOLVING,
        RESOLVED,
        FAILED
    }

    private String id;
    private String severity;          // CRITICAL, HIGH, MEDIUM, LOW
    private String title;
    private String detail;
    private String mitreId;           // e.g. T1059.004
    private String mitreName;         // e.g. "Unix Shell"
    private String host;
    private Instant timestamp;
    private AlertStatus status;

    // Source event linkage
    private String sourceEventId;
    private SystemEvent.EventType sourceEventType;

    // Actionable fields — what Blue can do with this alert
    private Map<String, Object> actionableFields;  // e.g. {"killPid": 1234, "blockIp": "10.0.0.30"}

    // Threat impact — how much this adds to threat score
    private int threatImpact;

    public GameAlert() {
        this.timestamp = Instant.now();
        this.status = AlertStatus.OPEN;
        this.actionableFields = new HashMap<>();
    }

    public GameAlert(String id, String severity, String title, String detail,
                     String mitreId, String mitreName, String host,
                     String sourceEventId, SystemEvent.EventType sourceEventType,
                     Map<String, Object> actionableFields, int threatImpact) {
        this();
        this.id = id;
        this.severity = severity;
        this.title = title;
        this.detail = detail;
        this.mitreId = mitreId;
        this.mitreName = mitreName;
        this.host = host;
        this.sourceEventId = sourceEventId;
        this.sourceEventType = sourceEventType;
        if (actionableFields != null) {
            this.actionableFields = actionableFields;
        }
        this.threatImpact = threatImpact;
    }

    // ===== Getters & Setters =====

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }

    public String getMitreId() { return mitreId; }
    public void setMitreId(String mitreId) { this.mitreId = mitreId; }

    public String getMitreName() { return mitreName; }
    public void setMitreName(String mitreName) { this.mitreName = mitreName; }

    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }

    public AlertStatus getStatus() { return status; }
    public void setStatus(AlertStatus status) { this.status = status; }

    public String getSourceEventId() { return sourceEventId; }
    public void setSourceEventId(String sourceEventId) { this.sourceEventId = sourceEventId; }

    public SystemEvent.EventType getSourceEventType() { return sourceEventType; }
    public void setSourceEventType(SystemEvent.EventType sourceEventType) { this.sourceEventType = sourceEventType; }

    public Map<String, Object> getActionableFields() { return actionableFields; }
    public void setActionableFields(Map<String, Object> actionableFields) { this.actionableFields = actionableFields; }

    public int getThreatImpact() { return threatImpact; }
    public void setThreatImpact(int threatImpact) { this.threatImpact = threatImpact; }

    public boolean isOpen() {
        return status == AlertStatus.OPEN || status == AlertStatus.FAILED;
    }
}
