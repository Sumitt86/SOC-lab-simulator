package com.minisoc.lab.service;

import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Mock antivirus engine validation service.
 * Simulates VirusTotal-style multi-engine hash scanning.
 * All detection results are deterministic — same hash always produces the same result.
 */
@Service
public class MockAvEngineService {

    // 14 mock AV engines matching the user's selection
    private static final List<String> ENGINES = List.of(
            "AliCloud", "Avast", "ClamAV", "DrWeb", "Google",
            "Ikarus", "Sangfor", "Engine Zero", "TrendMicro", "Varist",
            "AhnLab-V3", "Arcabit", "Avira", "BitDefender"
    );

    // Threat classification families
    private static final String[] TROJAN_NAMES = {
            "Trojan.Generic", "Trojan.Agent", "Trojan.Dropper", "Trojan.Downloader",
            "Trojan.Backdoor", "Trojan.ShellCmd", "Trojan.ReverseShell"
    };
    private static final String[] MALWARE_NAMES = {
            "Malware.Heuristic", "Malware.Packed", "Malware.Obfuscated",
            "Malware.BinaryAnomaly", "Malware.Suspicious"
    };
    private static final String[] RANSOMWARE_NAMES = {
            "Ransom.Generic", "Ransom.FileCryptor", "Ransom.Locker",
            "Ransom.CryptBot", "Ransom.Encoder"
    };
    private static final String[] WORM_NAMES = {
            "Worm.Generic", "Worm.NetSpread", "Worm.AutoRun"
    };
    private static final String[] EXPLOIT_NAMES = {
            "Exploit.ShellCode", "Exploit.CVE", "Exploit.BufferOverflow"
    };

    /**
     * Scan a hash against all mock AV engines.
     * Results are deterministic — same hash always returns the same result.
     *
     * @param sha256 the SHA-256 hash to scan
     * @return scan results with per-engine detections
     */
    public Map<String, Object> scanHash(String sha256) {
        if (sha256 == null || sha256.isBlank() || sha256.equals("unknown")) {
            return Map.of("success", false, "reason", "Invalid hash");
        }

        // Use hash to seed random for deterministic results
        int seed = sha256.hashCode();
        Random rand = new Random(seed);

        // Determine overall detection rate based on hash characteristics
        // Higher entropy hashes (more varied chars) → higher detection probability
        double hashEntropy = calculateStringEntropy(sha256);
        double baseDetectionProbability = Math.min(0.95, Math.max(0.1, (hashEntropy - 3.0) / 2.0));

        List<Map<String, Object>> engineResults = new ArrayList<>();
        int detected = 0;
        int total = ENGINES.size();

        for (String engine : ENGINES) {
            // Each engine has its own detection probability (seeded by hash + engine name)
            Random engineRand = new Random(seed + engine.hashCode());
            boolean detects = engineRand.nextDouble() < baseDetectionProbability;

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("engine", engine);
            result.put("detected", detects);

            if (detects) {
                detected++;
                String classification = generateClassification(engineRand);
                result.put("classification", classification);
                result.put("confidence", 60 + engineRand.nextInt(40)); // 60-99%
            } else {
                result.put("classification", "Clean");
                result.put("confidence", 0);
            }

            engineResults.add(result);
        }

        // Sort: detected first
        engineResults.sort((a, b) -> Boolean.compare((boolean) b.get("detected"), (boolean) a.get("detected")));

        double detectionRate = (double) detected / total * 100;
        String verdict;
        if (detectionRate >= 70) verdict = "MALICIOUS";
        else if (detectionRate >= 40) verdict = "SUSPICIOUS";
        else if (detectionRate > 0) verdict = "LOW_RISK";
        else verdict = "CLEAN";

        // Determine recommended action
        String recommendation;
        int confidencePoints;
        if (detectionRate >= 70) {
            recommendation = "DEPLOY_SIGNATURE";
            confidencePoints = 50;
        } else if (detectionRate >= 40) {
            recommendation = "INVESTIGATE_FURTHER";
            confidencePoints = 30;
        } else if (detectionRate > 0) {
            recommendation = "MONITOR";
            confidencePoints = 10;
        } else {
            recommendation = "NO_ACTION";
            confidencePoints = 0;
        }

        // Build first-seen/last-seen (deterministic)
        long daysSinceFirstSeen = Math.abs(seed % 365);
        String firstSeen = daysSinceFirstSeen + " days ago";

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("hash", sha256);
        response.put("engines", engineResults);
        response.put("detected", detected);
        response.put("total", total);
        response.put("detectionRate", Math.round(detectionRate * 10.0) / 10.0);
        response.put("verdict", verdict);
        response.put("recommendation", recommendation);
        response.put("confidencePoints", confidencePoints);
        response.put("firstSeen", firstSeen);
        response.put("communityScore", Math.abs(seed % 100));

        return response;
    }

    private String generateClassification(Random rand) {
        int category = rand.nextInt(5);
        return switch (category) {
            case 0 -> TROJAN_NAMES[rand.nextInt(TROJAN_NAMES.length)];
            case 1 -> MALWARE_NAMES[rand.nextInt(MALWARE_NAMES.length)];
            case 2 -> RANSOMWARE_NAMES[rand.nextInt(RANSOMWARE_NAMES.length)];
            case 3 -> WORM_NAMES[rand.nextInt(WORM_NAMES.length)];
            case 4 -> EXPLOIT_NAMES[rand.nextInt(EXPLOIT_NAMES.length)];
            default -> "Malware.Generic";
        };
    }

    private double calculateStringEntropy(String s) {
        if (s == null || s.isEmpty()) return 0;
        int[] freq = new int[256];
        for (char c : s.toCharArray()) freq[c & 0xFF]++;
        double entropy = 0;
        int len = s.length();
        for (int f : freq) {
            if (f == 0) continue;
            double p = (double) f / len;
            entropy -= p * (Math.log(p) / Math.log(2));
        }
        return entropy;
    }
}
