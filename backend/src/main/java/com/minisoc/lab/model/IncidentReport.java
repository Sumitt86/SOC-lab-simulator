package com.minisoc.lab.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record IncidentReport(
        String gameId,
        Instant startTime,
        Instant endTime,
        String winner,
        long durationSeconds,
        String difficulty,
        int redScore,
        int blueScore,
        int finalThreatScore,
        String finalPhase,
        List<SystemEvent> timeline,
        List<GameAlert> alertsRaised,
        List<DetectionSignature> signaturesDeployed,
        List<Map<String, Object>> blueActions,
        List<Map<String, Object>> redCommands
) {}
