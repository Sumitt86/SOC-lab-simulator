package com.minisoc.lab.model;

import java.util.List;

public record AnalysisReport(
        String filePath,
        String fileType,
        String sha256,
        double entropy,
        boolean highEntropy,
        String verdict,          // CLEAN / SUSPICIOUS / MALICIOUS
        int threatScore,         // 0–100
        List<String> extractedStrings,
        List<String> suspiciousStrings,
        String hexDump,
        List<DetectedIOC> iocs,
        List<ThreatMatch> threats
) {
    public record DetectedIOC(
            String type,     // C2_IP, C2_PORT, SHELL_PATTERN, HASH
            String value,
            String context   // surrounding text fragment
    ) {}

    public record ThreatMatch(
            String type,
            String name,
            String severity,
            String rule,
            String detail
    ) {}
}
