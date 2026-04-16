package com.minisoc.lab.model;

public enum Difficulty {
    EASY(20, 5, 0.10),
    MEDIUM(15, 3, 0.20),
    HARD(10, 2, 0.40);

    private final int escalationThresholdSeconds;
    private final int beaconThreshold;
    private final double falsePositiveRate;

    Difficulty(int escalationThresholdSeconds, int beaconThreshold, double falsePositiveRate) {
        this.escalationThresholdSeconds = escalationThresholdSeconds;
        this.beaconThreshold = beaconThreshold;
        this.falsePositiveRate = falsePositiveRate;
    }

    public int getEscalationThresholdSeconds() {
        return escalationThresholdSeconds;
    }

    public int getBeaconThreshold() {
        return beaconThreshold;
    }

    public double getFalsePositiveRate() {
        return falsePositiveRate;
    }
}
