package com.minisoc.lab.service;

import com.minisoc.lab.model.GameAlert;
import com.minisoc.lab.model.SystemEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Translates raw SystemEvents from the monitoring agent into
 * actionable GameAlerts with MITRE ATT&CK mapping.
 *
 * This is the "intelligence layer" — not every event becomes an alert,
 * and alerts carry actionable context that Blue Team can act on.
 */
@Component
public class EventAlertMapper {

    private static final Logger log = LoggerFactory.getLogger(EventAlertMapper.class);

    // Suspicious process names that should always trigger alerts
    private static final Pattern SUSPICIOUS_PROCESS = Pattern.compile(
            "\\b(nc|ncat|netcat|socat|nmap|python3?|perl|ruby|bash|sh|curl|wget)\\b",
            Pattern.CASE_INSENSITIVE
    );

    // IP extraction pattern for cmdline analysis
    private static final Pattern IP_PATTERN = Pattern.compile(
            "(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3})"
    );

    /**
     * Map a raw system event to a game alert.
     * Returns null if the event is not alertable (benign noise).
     */
    public GameAlert map(SystemEvent event) {
        if (event == null || event.getType() == null) return null;

        return switch (event.getType()) {
            case PROCESS_SPAWNED -> mapProcessSpawned(event);
            case NETWORK_CONNECTION -> mapNetworkConnection(event);
            case FILE_CREATED, FILE_MODIFIED -> mapFileEvent(event);
            case CRON_ADDED -> mapCronAdded(event);
            case PROCESS_TERMINATED -> null; // confirmation, not alert
            case CRON_REMOVED -> null; // defensive action confirmation
            case ANOMALY_DETECTED -> mapAnomaly(event);
        };
    }

    private GameAlert mapProcessSpawned(SystemEvent event) {
        String processName = event.getProcessName();
        String cmdline = event.getCmdline();

        if (processName == null) return null;

        // Check if process name is suspicious
        if (!SUSPICIOUS_PROCESS.matcher(processName).find() &&
                (cmdline == null || !SUSPICIOUS_PROCESS.matcher(cmdline).find())) {
            return null; // benign process
        }

        // Determine severity and MITRE based on specifics
        String severity = "HIGH";
        String mitreId = "T1059";
        String mitreName = "Command and Scripting Interpreter";
        String title = "Suspicious Process Execution";
        String detail;
        int threatImpact = 15;

        if (isReverseShell(cmdline)) {
            severity = "CRITICAL";
            mitreId = "T1059.004";
            mitreName = "Unix Shell";
            title = "Reverse Shell Detected";
            detail = String.format("Process '%s' (PID %d) appears to be a reverse shell: %s",
                    processName, event.getPid(), truncate(cmdline, 120));
            threatImpact = 30;
        } else if (isBeaconLoop(cmdline)) {
            severity = "CRITICAL";
            mitreId = "T1071.001";
            mitreName = "Web Protocols";
            title = "C2 Beacon Loop Detected";
            detail = String.format("Recurring beacon process '%s' (PID %d): %s",
                    processName, event.getPid(), truncate(cmdline, 120));
            threatImpact = 25;
        } else if (isNetcatListener(cmdline)) {
            severity = "CRITICAL";
            mitreId = "T1571";
            mitreName = "Non-Standard Port";
            title = "Netcat Listener Active";
            detail = String.format("Listening socket via '%s' (PID %d): %s",
                    processName, event.getPid(), truncate(cmdline, 120));
            threatImpact = 25;
        } else {
            detail = String.format("Suspicious process '%s' (PID %d, PPID %d, user: %s): %s",
                    processName, event.getPid(),
                    event.getPpid() != null ? event.getPpid() : -1,
                    event.getUser() != null ? event.getUser() : "unknown",
                    truncate(cmdline, 120));
        }

        // Build actionable fields
        Map<String, Object> actions = new HashMap<>();
        if (event.getPid() != null) {
            actions.put("killPid", event.getPid());
        }
        String extractedIp = extractIpFromCmdline(cmdline);
        if (extractedIp != null) {
            actions.put("blockIp", extractedIp);
        }

        return new GameAlert(
                generateAlertId(),
                severity, title, detail,
                mitreId, mitreName,
                event.getHost(),
                event.getId(), event.getType(),
                actions, threatImpact
        );
    }

    private GameAlert mapNetworkConnection(SystemEvent event) {
        String severity = "HIGH";
        String title = "Suspicious Network Connection";
        String mitreId = "T1071";
        String mitreName = "Application Layer Protocol";
        int threatImpact = 20;

        String detail = String.format("Connection %s:%d → %s:%d (state: %s, process: %s)",
                event.getLocalIp() != null ? event.getLocalIp() : "?",
                event.getLocalPort() != null ? event.getLocalPort() : 0,
                event.getRemoteIp() != null ? event.getRemoteIp() : "?",
                event.getRemotePort() != null ? event.getRemotePort() : 0,
                event.getConnectionState() != null ? event.getConnectionState() : "?",
                event.getProcessName() != null ? event.getProcessName() : "unknown");

        // If outbound to known C2 pattern → CRITICAL
        if ("ESTABLISHED".equalsIgnoreCase(event.getConnectionState())) {
            severity = "CRITICAL";
            title = "Command & Control Connection Established";
            mitreId = "T1071.001";
            threatImpact = 30;
        }

        Map<String, Object> actions = new HashMap<>();
        if (event.getRemoteIp() != null) {
            actions.put("blockIp", event.getRemoteIp());
        }
        if (event.getPid() != null) {
            actions.put("killPid", event.getPid());
        }

        return new GameAlert(
                generateAlertId(),
                severity, title, detail,
                mitreId, mitreName,
                event.getHost(),
                event.getId(), event.getType(),
                actions, threatImpact
        );
    }

    private GameAlert mapFileEvent(SystemEvent event) {
        String filePath = event.getFilePath();
        if (filePath == null) return null;

        // Only alert on suspicious file patterns
        boolean isEncrypted = filePath.endsWith(".encrypted") || filePath.endsWith(".locked");
        boolean isTmpScript = filePath.startsWith("/tmp/") &&
                (filePath.endsWith(".sh") || filePath.endsWith(".py"));
        boolean isSuspiciousPath = filePath.contains("/.") && !filePath.contains("/.git");

        if (!isEncrypted && !isTmpScript && !isSuspiciousPath) {
            return null; // benign file operation
        }

        String severity;
        String title;
        String mitreId;
        String mitreName;
        int threatImpact;

        if (isEncrypted) {
            severity = "CRITICAL";
            title = "Ransomware File Encryption Detected";
            mitreId = "T1486";
            mitreName = "Data Encrypted for Impact";
            threatImpact = 35;
        } else if (isTmpScript) {
            severity = "HIGH";
            title = "Suspicious Script Dropped";
            mitreId = "T1105";
            mitreName = "Ingress Tool Transfer";
            threatImpact = 15;
        } else {
            severity = "MEDIUM";
            title = "Hidden File Created";
            mitreId = "T1564.001";
            mitreName = "Hidden Files and Directories";
            threatImpact = 10;
        }

        String detail = String.format("File %s: %s (action: %s)",
                event.getType() == SystemEvent.EventType.FILE_CREATED ? "created" : "modified",
                filePath,
                event.getFileAction() != null ? event.getFileAction() : "unknown");

        return new GameAlert(
                generateAlertId(),
                severity, title, detail,
                mitreId, mitreName,
                event.getHost(),
                event.getId(), event.getType(),
                Map.of("filePath", filePath),
                threatImpact
        );
    }

    private GameAlert mapCronAdded(SystemEvent event) {
        String detail = String.format("New cron job detected on %s: %s",
                event.getHost() != null ? event.getHost() : "unknown",
                event.getCronEntry() != null ? event.getCronEntry() : "unknown entry");

        Map<String, Object> actions = new HashMap<>();
        if (event.getCronEntry() != null) {
            actions.put("removeCron", event.getCronEntry());
        }

        return new GameAlert(
                generateAlertId(),
                "CRITICAL",
                "Malicious Persistence — Cron Job",
                detail,
                "T1053.003", "Cron",
                event.getHost(),
                event.getId(), event.getType(),
                actions, 20
        );
    }

    private GameAlert mapAnomaly(SystemEvent event) {
        return new GameAlert(
                generateAlertId(),
                event.getRiskScore() != null && event.getRiskScore() >= 80 ? "CRITICAL" : "HIGH",
                "Anomaly Detected: " + (event.getAnomalyReason() != null ? event.getAnomalyReason() : "Unknown"),
                event.getDetails() != null ? event.getDetails().toString() : "No additional details",
                "T1203", "Exploitation for Client Execution",
                event.getHost(),
                event.getId(), event.getType(),
                Map.of(), 15
        );
    }

    // ===== Helpers =====

    private boolean isReverseShell(String cmdline) {
        if (cmdline == null) return false;
        return cmdline.contains("-e /bin/bash") || cmdline.contains("-e /bin/sh")
                || cmdline.contains("bash -i") || cmdline.contains("/dev/tcp/");
    }

    private boolean isBeaconLoop(String cmdline) {
        if (cmdline == null) return false;
        return cmdline.contains("while true") || cmdline.contains("while :") ||
                (cmdline.contains("sleep") && (cmdline.contains("nc ") || cmdline.contains("curl ")));
    }

    private boolean isNetcatListener(String cmdline) {
        if (cmdline == null) return false;
        return (cmdline.contains("nc ") || cmdline.contains("ncat "))
                && (cmdline.contains("-l") || cmdline.contains("--listen"));
    }

    private String extractIpFromCmdline(String cmdline) {
        if (cmdline == null) return null;
        Matcher matcher = IP_PATTERN.matcher(cmdline);
        while (matcher.find()) {
            String ip = matcher.group(1);
            // Skip localhost and 0.0.0.0
            if (!"127.0.0.1".equals(ip) && !"0.0.0.0".equals(ip)) {
                return ip;
            }
        }
        return null;
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() > maxLen ? s.substring(0, maxLen) + "…" : s;
    }

    private String generateAlertId() {
        return "ALT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
