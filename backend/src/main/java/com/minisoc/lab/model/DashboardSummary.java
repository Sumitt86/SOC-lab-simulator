package com.minisoc.lab.model;

public record DashboardSummary(
        int activeAlerts,
        int criticalAlerts,
        int eventsPerMinute,
        int beaconCount,
        int blocksFired,
        int simulationPhase
) {
}
