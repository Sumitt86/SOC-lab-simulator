package com.minisoc.lab.service;

import com.minisoc.lab.model.AttackPhase;
import com.minisoc.lab.model.DetectionRule;
import com.minisoc.lab.model.DetectionRule.LogSource;
import com.minisoc.lab.model.DetectionRule.Severity;
import com.minisoc.lab.model.GameAlert;
import com.minisoc.lab.model.GameState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;

/**
 * Parses raw log lines from victim container, matches them against detection rules,
 * and generates MITRE-mapped alerts for the blue team.
 */
@Service
public class DetectionService {

    private static final Logger log = LoggerFactory.getLogger(DetectionService.class);

    private final SimulationService simulationService;
    private final StreamingService streamingService;
    private final CorrelationService correlationService;
    private final ScoringService scoringService;
    private final GameStateService gameStateService;
    private final GameState gameState;

    private final Map<String, DetectionRule> rules = new LinkedHashMap<>();
    private final Map<String, Pattern> compiledPatterns = new ConcurrentHashMap<>();
    private final List<Map<String, Object>> rawLogs = Collections.synchronizedList(new ArrayList<>());
    private final List<GameAlert> detectionAlerts = new CopyOnWriteArrayList<>();

    // Track recent matches to avoid duplicate alerts (rule+message hash → timestamp)
    private final Map<String, Instant> recentMatches = new ConcurrentHashMap<>();
    private static final long DEDUP_WINDOW_SECONDS = 10;

    public DetectionService(SimulationService simulationService,
                            StreamingService streamingService,
                            CorrelationService correlationService,
                            ScoringService scoringService,
                            GameStateService gameStateService,
                            GameState gameState) {
        this.simulationService = simulationService;
        this.streamingService = streamingService;
        this.correlationService = correlationService;
        this.scoringService = scoringService;
        this.gameStateService = gameStateService;
        this.gameState = gameState;
        initializeRules();
    }

    private void initializeRules() {
        addRule(new DetectionRule("DET-001", "SQL Injection Attempt",
                "T1190", "Initial Access", LogSource.APACHE,
                "(?i)(union\\s+select|or\\s+'1'\\s*=\\s*'1|or\\s+1\\s*=\\s*1|'\\s*--|sleep\\s*\\(|benchmark\\s*\\(|0x[0-9a-f]{6,}|information_schema|' or ''=')",
                Severity.HIGH));

        addRule(new DetectionRule("DET-002", "Remote Code Execution via Web",
                "T1059.004", "Execution", LogSource.APACHE,
                "(?i)(cmd=|exec=|system\\(|passthru|shell_exec|popen|proc_open|\\bwhoami\\b|/etc/passwd|/etc/shadow|base64.*decode)",
                Severity.CRITICAL));

        addRule(new DetectionRule("DET-003", "SSH Brute Force",
                "T1110.001", "Credential Access", LogSource.SSH,
                "(?i)(Failed password|authentication failure|Invalid user|maximum authentication attempts)",
                Severity.HIGH));

        addRule(new DetectionRule("DET-004", "SSH Successful Login After Failures",
                "T1078", "Initial Access", LogSource.SSH,
                "(?i)(Accepted password|session opened for user)",
                Severity.MEDIUM));

        addRule(new DetectionRule("DET-005", "Privilege Escalation via Sudo",
                "T1548.003", "Privilege Escalation", LogSource.SSH,
                "(?i)(sudo:.*COMMAND=|sudo.*root|NOPASSWD)",
                Severity.CRITICAL));

        addRule(new DetectionRule("DET-006", "Suspicious DNS Query",
                "T1572", "Command and Control", LogSource.DNS,
                "(?i)(exfil|tunnel|evil\\.com|base64|[a-z0-9]{30,}\\.)",
                Severity.HIGH));

        addRule(new DetectionRule("DET-007", "DNS Zone Transfer Attempt",
                "T1018", "Discovery", LogSource.DNS,
                "(?i)(AXFR|query\\[ANY\\]|query\\[TXT\\].*long)",
                Severity.MEDIUM));

        addRule(new DetectionRule("DET-008", "Web Shell Upload",
                "T1505.003", "Persistence", LogSource.APACHE,
                "(?i)(uploads/.*\\.php|shell\\.php|webshell|c99|r57|b374k)",
                Severity.CRITICAL));

        addRule(new DetectionRule("DET-009", "Cron Job Modification",
                "T1053.003", "Persistence", LogSource.AUDIT,
                "(?i)(crontab|cron\\.d|/var/spool/cron|CRON.*CMD)",
                Severity.HIGH));

        addRule(new DetectionRule("DET-010", "Sensitive File Access",
                "T1552.001", "Credential Access", LogSource.AUDIT,
                "(?i)(\\.env|config\\.php|shadow|authorized_keys|id_rsa|employee_records|network_map)",
                Severity.MEDIUM));

        addRule(new DetectionRule("DET-011", "Data Exfiltration Indicator",
                "T1048.003", "Exfiltration", LogSource.NETWORK,
                "(?i)(tar\\s+[cz]|base64.*curl|curl.*upload|nc\\s+-w|/tmp/\\.data)",
                Severity.CRITICAL));

        addRule(new DetectionRule("DET-012", "Network Scanning",
                "T1046", "Discovery", LogSource.APACHE,
                "(?i)(nmap|masscan|nikto|dirbuster|gobuster|sqlmap)",
                Severity.MEDIUM));

        addRule(new DetectionRule("DET-013", "File Enumeration",
                "T1083", "Discovery", LogSource.AUDIT,
                "(?i)(find\\s+/.*-name|locate\\s|find.*\\.csv|find.*\\.key|find.*\\.pem)",
                Severity.MEDIUM));
    }

    private void addRule(DetectionRule rule) {
        rules.put(rule.getRuleId(), rule);
        compiledPatterns.put(rule.getRuleId(), Pattern.compile(rule.getPattern()));
    }

    /**
     * Process a raw log line from the victim container.
     * Returns any alerts generated.
     */
    public List<GameAlert> processLog(String source, String message) {
        if (message == null || message.isBlank()) return List.of();

        // Store raw log
        rawLogs.add(Map.of(
                "source", source,
                "message", message,
                "timestamp", Instant.now().toString()
        ));

        // Trim raw log buffer
        while (rawLogs.size() > 5000) {
            rawLogs.remove(0);
        }

        LogSource logSource;
        try {
            logSource = LogSource.valueOf(source.toUpperCase());
        } catch (IllegalArgumentException e) {
            logSource = null;
        }

        List<GameAlert> alerts = new ArrayList<>();

        for (Map.Entry<String, DetectionRule> entry : rules.entrySet()) {
            DetectionRule rule = entry.getValue();
            if (!rule.isEnabled()) continue;

            // Match by log source if available, or match all
            if (logSource != null && rule.getLogSource() != logSource) continue;

            Pattern pattern = compiledPatterns.get(rule.getRuleId());
            if (pattern != null && pattern.matcher(message).find()) {
                // Dedup check
                String dedupKey = rule.getRuleId() + ":" + message.hashCode();
                Instant lastMatch = recentMatches.get(dedupKey);
                if (lastMatch != null && Instant.now().minusSeconds(DEDUP_WINDOW_SECONDS).isBefore(lastMatch)) {
                    continue;
                }
                recentMatches.put(dedupKey, Instant.now());

                GameAlert alert = new GameAlert(
                        UUID.randomUUID().toString().substring(0, 8),
                        rule.getSeverity().name(),
                        rule.getName() + " [" + rule.getRuleId() + "]",
                        "Source: " + source + " | " + truncate(message, 200),
                        rule.getMitreId(),
                        rule.getName(),
                        "soc-victim",
                        null, null, null,
                        rule.getSeverity() == Severity.CRITICAL ? 15 :
                                rule.getSeverity() == Severity.HIGH ? 10 : 5
                );

                alerts.add(alert);
                detectionAlerts.add(alert);

                // Push to SSE stream for real-time blue team visibility
                streamingService.pushAlert(alert);

                // Push to simulation service
                simulationService.pushLog("WARN",
                        String.format("[%s] %s detected: %s (%s)",
                                rule.getRuleId(), rule.getMitreId(),
                                rule.getName(), rule.getSeverity()));

                log.info("DETECTION [{}] {} → {} ({})", rule.getRuleId(),
                        rule.getMitreId(), rule.getName(), source);

                // Run correlation engine
                correlationService.correlate(alert);
                
                // Award blue team points for detection
                scoringService.awardDetectionPoints(gameState, rule.getMitreId(), "detection");
                
                // Check for speed bonus on first detection
                if (detectionAlerts.size() == 1) {
                    scoringService.checkBlueSpeedBonus(gameState, rule.getMitreId());
                }
                
                // Check if this is early detection (initial access phase)
                if (gameState.getPhase() == AttackPhase.INITIAL_ACCESS) {
                    // Award bonus for early detection, trigger game state check
                    gameStateService.checkEndConditions();
                }
            }
        }

        return alerts;
    }

    // ===== Query methods =====

    public List<DetectionRule> getAllRules() {
        return new ArrayList<>(rules.values());
    }

    public DetectionRule getRule(String ruleId) {
        return rules.get(ruleId);
    }

    public void toggleRule(String ruleId, boolean enabled) {
        DetectionRule rule = rules.get(ruleId);
        if (rule != null) rule.setEnabled(enabled);
    }

    public List<GameAlert> getDetectionAlerts() {
        return new ArrayList<>(detectionAlerts);
    }

    public List<Map<String, Object>> getRawLogs(int limit, String sourceFilter) {
        List<Map<String, Object>> filtered = rawLogs;
        if (sourceFilter != null && !sourceFilter.isBlank()) {
            filtered = rawLogs.stream()
                    .filter(l -> sourceFilter.equalsIgnoreCase((String) l.get("source")))
                    .toList();
        }
        int start = Math.max(0, filtered.size() - limit);
        return new ArrayList<>(filtered.subList(start, filtered.size()));
    }

    public Map<String, Long> getDetectionStats() {
        Map<String, Long> stats = new LinkedHashMap<>();
        for (GameAlert alert : detectionAlerts) {
            String key = alert.getMitreId() != null ? alert.getMitreId() : "UNKNOWN";
            stats.merge(key, 1L, Long::sum);
        }
        return stats;
    }

    private static String truncate(String s, int max) {
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }
}
