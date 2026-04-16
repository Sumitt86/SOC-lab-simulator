package com.minisoc.lab.model;

public record DashboardSummary(
        int activeAlerts,
        int criticalAlerts,
        int eventsPerMinute,
        int beaconCount,
        int blocksFired,
        int simulationPhase,
        String attackPhase,
        String attackPhaseDisplay,
        int threatScore,
        String threatLevel,
        String gameStatus,
        int redScore,
        int blueScore,
        long elapsedSeconds,
        boolean persistenceActive,
        int activeHostCount,
        String difficulty,
        String winReason
) {
}
