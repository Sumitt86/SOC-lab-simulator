package com.minisoc.lab.model;

import java.util.HashSet;
import java.util.Set;

public class GameState {

    private AttackPhase phase = AttackPhase.INITIAL_ACCESS;
    private GameStatus status = GameStatus.ACTIVE;
    private Difficulty difficulty = Difficulty.MEDIUM;

    private int threatScore;
    private int beaconCount;
    private boolean persistenceActive;
    private final Set<String> activeHosts = new HashSet<>();
    private final Set<String> blockedIPs = new HashSet<>();
    private final Set<String> isolatedHosts = new HashSet<>();

    private int redScore;
    private int blueScore;

    private long startTimeMillis;
    private long phaseStartTimeMillis;
    private String winReason = "";

    public GameState() {
        long now = System.currentTimeMillis();
        this.startTimeMillis = now;
        this.phaseStartTimeMillis = now;
        this.activeHosts.add("host-001");
    }

    // ===== Computed =====

    public long getElapsedSeconds() {
        return (System.currentTimeMillis() - startTimeMillis) / 1000;
    }

    public long getPhaseElapsedSeconds() {
        return (System.currentTimeMillis() - phaseStartTimeMillis) / 1000;
    }

    public int computeThreatScore() {
        int score = 0;
        score += beaconCount * 5;
        score += activeHosts.size() * 15;
        score += persistenceActive ? 20 : 0;
        score += (phase.getLevel() - 1) * 10;
        return Math.min(score, 100);
    }

    public String getThreatLevel() {
        if (threatScore >= 60) return "HIGH";
        if (threatScore >= 30) return "MEDIUM";
        return "LOW";
    }

    // ===== Getters & Setters =====

    public AttackPhase getPhase() { return phase; }
    public void setPhase(AttackPhase phase) {
        this.phase = phase;
        this.phaseStartTimeMillis = System.currentTimeMillis();
    }

    public GameStatus getStatus() { return status; }
    public void setStatus(GameStatus status) { this.status = status; }

    public Difficulty getDifficulty() { return difficulty; }
    public void setDifficulty(Difficulty difficulty) { this.difficulty = difficulty; }

    public int getThreatScore() { return threatScore; }
    public void setThreatScore(int threatScore) { this.threatScore = Math.max(0, Math.min(threatScore, 100)); }

    public int getBeaconCount() { return beaconCount; }
    public void setBeaconCount(int beaconCount) { this.beaconCount = Math.max(0, beaconCount); }

    public boolean isPersistenceActive() { return persistenceActive; }
    public void setPersistenceActive(boolean persistenceActive) { this.persistenceActive = persistenceActive; }

    public Set<String> getActiveHosts() { return activeHosts; }
    public Set<String> getBlockedIPs() { return blockedIPs; }
    public Set<String> getIsolatedHosts() { return isolatedHosts; }

    public int getRedScore() { return redScore; }
    public void setRedScore(int redScore) { this.redScore = redScore; }
    public void addRedScore(int points) { this.redScore += points; }

    public int getBlueScore() { return blueScore; }
    public void setBlueScore(int blueScore) { this.blueScore = blueScore; }
    public void addBlueScore(int points) { this.blueScore += points; }

    public long getStartTimeMillis() { return startTimeMillis; }
    public void setStartTimeMillis(long startTimeMillis) { this.startTimeMillis = startTimeMillis; }

    public String getWinReason() { return winReason; }
    public void setWinReason(String winReason) { this.winReason = winReason; }
}
