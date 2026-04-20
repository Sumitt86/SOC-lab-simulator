package com.minisoc.lab.model;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks the state of an active attack session — which vector is being run,
 * which kill chain stages have completed, score, and timeline.
 */
public class AttackSession {

    public enum SessionStatus { ACTIVE, COMPLETED, DETECTED, BLOCKED }

    private String sessionId;
    private String vectorId;
    private SessionStatus status;
    private Instant startTime;
    private Instant endTime;
    private int currentStageIndex;
    private int totalScore;
    private final Set<String> completedStages = ConcurrentHashMap.newKeySet();
    private final List<StageResult> stageResults = Collections.synchronizedList(new ArrayList<>());

    public AttackSession() {}

    public AttackSession(String vectorId) {
        this.sessionId = UUID.randomUUID().toString().substring(0, 8);
        this.vectorId = vectorId;
        this.status = SessionStatus.ACTIVE;
        this.startTime = Instant.now();
        this.currentStageIndex = 0;
        this.totalScore = 0;
    }

    public String getSessionId() { return sessionId; }
    public String getVectorId() { return vectorId; }
    public SessionStatus getStatus() { return status; }
    public void setStatus(SessionStatus status) { this.status = status; }
    public Instant getStartTime() { return startTime; }
    public Instant getEndTime() { return endTime; }
    public void setEndTime(Instant endTime) { this.endTime = endTime; }
    public int getCurrentStageIndex() { return currentStageIndex; }
    public void setCurrentStageIndex(int idx) { this.currentStageIndex = idx; }
    public int getTotalScore() { return totalScore; }
    public Set<String> getCompletedStages() { return completedStages; }
    public List<StageResult> getStageResults() { return stageResults; }

    public void addScore(int points) { this.totalScore += points; }

    public void completeStage(String stageId, int points, String output) {
        completedStages.add(stageId);
        totalScore += points;
        stageResults.add(new StageResult(stageId, points, output, Instant.now()));
    }

    public boolean isStageCompleted(String stageId) {
        return completedStages.contains(stageId);
    }

    /**
     * Result of executing a single kill chain stage.
     */
    public static class StageResult {
        private final String stageId;
        private final int points;
        private final String output;
        private final Instant timestamp;

        public StageResult(String stageId, int points, String output, Instant timestamp) {
            this.stageId = stageId;
            this.points = points;
            this.output = output;
            this.timestamp = timestamp;
        }

        public String getStageId() { return stageId; }
        public int getPoints() { return points; }
        public String getOutput() { return output; }
        public Instant getTimestamp() { return timestamp; }
    }
}
