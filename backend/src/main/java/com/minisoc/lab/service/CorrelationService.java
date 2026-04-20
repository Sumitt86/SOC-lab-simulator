package com.minisoc.lab.service;

import com.minisoc.lab.model.GameAlert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Multi-event correlation engine that detects attack chains spanning
 * multiple MITRE tactics. Correlated chains score higher than individual alerts.
 */
@Service
public class CorrelationService {

    private static final Logger log = LoggerFactory.getLogger(CorrelationService.class);

    /**
     * A correlated attack chain detected across multiple stages.
     */
    public static class CorrelatedChain {
        private final String chainId;
        private final String name;
        private final List<String> alertIds = new ArrayList<>();
        private final List<String> mitreIds = new ArrayList<>();
        private final List<String> tactics = new ArrayList<>();
        private Instant firstSeen;
        private Instant lastSeen;
        private int score;
        private String status; // ACTIVE, BLOCKED, COMPLETED

        public CorrelatedChain(String chainId, String name) {
            this.chainId = chainId;
            this.name = name;
            this.firstSeen = Instant.now();
            this.lastSeen = Instant.now();
            this.score = 0;
            this.status = "ACTIVE";
        }

        public void addAlert(GameAlert alert) {
            alertIds.add(alert.getId());
            if (alert.getMitreId() != null && !mitreIds.contains(alert.getMitreId())) {
                mitreIds.add(alert.getMitreId());
            }
            lastSeen = Instant.now();
        }

        public String getChainId() { return chainId; }
        public String getName() { return name; }
        public List<String> getAlertIds() { return alertIds; }
        public List<String> getMitreIds() { return mitreIds; }
        public List<String> getTactics() { return tactics; }
        public Instant getFirstSeen() { return firstSeen; }
        public Instant getLastSeen() { return lastSeen; }
        public int getScore() { return score; }
        public void setScore(int score) { this.score = score; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }

    // Known attack chain patterns (ordered MITRE tactic progression)
    private static final List<ChainPattern> CHAIN_PATTERNS = List.of(
            new ChainPattern("CHAIN-001", "Web Application Attack Chain",
                    List.of("T1190", "T1059.004", "T1505.003", "T1041"), 100),
            new ChainPattern("CHAIN-002", "SSH Brute Force Chain",
                    List.of("T1046", "T1110.001", "T1078", "T1548.003"), 100),
            new ChainPattern("CHAIN-003", "DNS Exfiltration Chain",
                    List.of("T1018", "T1572", "T1048.003"), 80),
            new ChainPattern("CHAIN-004", "Persistence Chain",
                    List.of("T1053.003", "T1046"), 60),
            new ChainPattern("CHAIN-005", "Data Theft Chain",
                    List.of("T1083", "T1552.001", "T1005", "T1048.003"), 100)
    );

    private static class ChainPattern {
        final String id;
        final String name;
        final List<String> expectedTechniques;
        final int maxScore;

        ChainPattern(String id, String name, List<String> expectedTechniques, int maxScore) {
            this.id = id;
            this.name = name;
            this.expectedTechniques = expectedTechniques;
            this.maxScore = maxScore;
        }
    }

    // Active correlation state
    private final Map<String, Set<String>> detectedTechniquesBySource = new ConcurrentHashMap<>();
    private final List<CorrelatedChain> correlatedChains = new CopyOnWriteArrayList<>();
    private final Map<String, CorrelatedChain> activeChains = new ConcurrentHashMap<>();

    // Correlation window: techniques must appear within this many seconds
    private static final long CORRELATION_WINDOW_SECONDS = 300; // 5 minutes

    private final StreamingService streamingService;

    public CorrelationService(StreamingService streamingService) {
        this.streamingService = streamingService;
    }

    /**
     * Process a detection alert and check for multi-event correlations.
     * Returns any newly correlated chains.
     */
    public List<CorrelatedChain> correlate(GameAlert alert) {
        if (alert.getMitreId() == null) return List.of();

        String host = alert.getHost() != null ? alert.getHost() : "unknown";
        detectedTechniquesBySource
                .computeIfAbsent(host, k -> ConcurrentHashMap.newKeySet())
                .add(alert.getMitreId());

        Set<String> hostTechniques = detectedTechniquesBySource.getOrDefault(host, Set.of());

        List<CorrelatedChain> newChains = new ArrayList<>();

        for (ChainPattern pattern : CHAIN_PATTERNS) {
            // Check if this alert completes or advances a known chain
            if (!pattern.expectedTechniques.contains(alert.getMitreId())) continue;

            // Count how many of the pattern's techniques have been seen
            long matchCount = pattern.expectedTechniques.stream()
                    .filter(hostTechniques::contains)
                    .count();

            // Require at least 2 matching techniques for correlation
            if (matchCount < 2) continue;

            // Check if chain already tracked
            CorrelatedChain chain = activeChains.get(pattern.id + ":" + host);
            if (chain == null) {
                chain = new CorrelatedChain(pattern.id + ":" + host, pattern.name);
                activeChains.put(pattern.id + ":" + host, chain);
                correlatedChains.add(chain);
                newChains.add(chain);

                log.info("CORRELATION: New chain detected - {} on {} ({}/{})",
                        pattern.name, host, matchCount, pattern.expectedTechniques.size());
            }

            chain.addAlert(alert);

            // Score based on completeness
            double completeness = (double) matchCount / pattern.expectedTechniques.size();
            chain.setScore((int) (pattern.maxScore * completeness));

            // If all techniques matched, chain is complete
            if (matchCount == pattern.expectedTechniques.size()) {
                chain.setStatus("COMPLETED");
                log.warn("CORRELATION: Full attack chain completed - {} on {}", pattern.name, host);

                // Push a CRITICAL correlated alert
                GameAlert correlatedAlert = new GameAlert(
                        "corr-" + UUID.randomUUID().toString().substring(0, 6),
                        "CRITICAL",
                        "Correlated Attack Chain: " + pattern.name,
                        "Full kill chain detected: " + String.join(" → ", pattern.expectedTechniques) +
                                " | Score: " + chain.getScore() + " pts",
                        pattern.expectedTechniques.get(pattern.expectedTechniques.size() - 1),
                        pattern.name,
                        host,
                        null, null,
                        Map.of("chainId", chain.getChainId(),
                                "techniques", pattern.expectedTechniques,
                                "chainScore", chain.getScore()),
                        chain.getScore()
                );

                streamingService.pushAlert(correlatedAlert);
            }
        }

        return newChains;
    }

    /**
     * Get all detected correlation chains.
     */
    public List<CorrelatedChain> getCorrelatedChains() {
        return new ArrayList<>(correlatedChains);
    }

    /**
     * Get correlation statistics.
     */
    public Map<String, Object> getCorrelationStats() {
        long activeCount = correlatedChains.stream().filter(c -> "ACTIVE".equals(c.getStatus())).count();
        long completedCount = correlatedChains.stream().filter(c -> "COMPLETED".equals(c.getStatus())).count();
        long blockedCount = correlatedChains.stream().filter(c -> "BLOCKED".equals(c.getStatus())).count();
        int totalScore = correlatedChains.stream().mapToInt(CorrelatedChain::getScore).sum();

        return Map.of(
                "totalChains", correlatedChains.size(),
                "activeChains", activeCount,
                "completedChains", completedCount,
                "blockedChains", blockedCount,
                "totalCorrelationScore", totalScore
        );
    }

    /**
     * Mark a chain as blocked (blue team remediated).
     */
    public void blockChain(String chainId) {
        CorrelatedChain chain = activeChains.get(chainId);
        if (chain != null) {
            chain.setStatus("BLOCKED");
            log.info("CORRELATION: Chain blocked - {}", chainId);
        }
    }

    /**
     * Reset all correlation state.
     */
    public void reset() {
        detectedTechniquesBySource.clear();
        correlatedChains.clear();
        activeChains.clear();
    }
}
