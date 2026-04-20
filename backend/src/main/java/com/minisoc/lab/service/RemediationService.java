package com.minisoc.lab.service;

import com.minisoc.lab.executor.SystemCommandExecutor;
import com.minisoc.lab.model.CommandResult;
import com.minisoc.lab.model.GameAlert;
import com.minisoc.lab.model.GameState;
import com.minisoc.lab.model.LogEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Automated remediation service that executes response actions
 * against detected threats on the victim container.
 */
@Service
public class RemediationService {

    private static final Logger log = LoggerFactory.getLogger(RemediationService.class);

    private static final String VICTIM_CONTAINER = "soc-victim";

    /**
     * Record of a remediation action taken.
     */
    public static class RemediationAction {
        private final String actionId;
        private final String alertId;
        private final String mitreId;
        private final String actionType;
        private final String command;
        private final String status; // SUCCESS, FAILED
        private final String result;
        private final Instant timestamp;

        public RemediationAction(String actionId, String alertId, String mitreId,
                                  String actionType, String command, String status, String result) {
            this.actionId = actionId;
            this.alertId = alertId;
            this.mitreId = mitreId;
            this.actionType = actionType;
            this.command = command;
            this.status = status;
            this.result = result;
            this.timestamp = Instant.now();
        }

        public String getActionId() { return actionId; }
        public String getAlertId() { return alertId; }
        public String getMitreId() { return mitreId; }
        public String getActionType() { return actionType; }
        public String getCommand() { return command; }
        public String getStatus() { return status; }
        public String getResult() { return result; }
        public Instant getTimestamp() { return timestamp; }
    }

    // Available remediation actions per MITRE technique
    private static final Map<String, List<RemediationOption>> TECHNIQUE_ACTIONS = new LinkedHashMap<>();

    static {
        TECHNIQUE_ACTIONS.put("T1190", List.of(
                new RemediationOption("block_ip", "Block Source IP", "iptables -A INPUT -s %s -j DROP"),
                new RemediationOption("restart_apache", "Restart Apache", "service apache2 restart"),
                new RemediationOption("disable_admin", "Disable Admin Endpoint", "rm -f /var/www/html/admin.php")
        ));
        TECHNIQUE_ACTIONS.put("T1059.004", List.of(
                new RemediationOption("kill_shell", "Kill Suspicious Shells", "pkill -f 'bash -i' ; pkill -f '/dev/tcp'"),
                new RemediationOption("restrict_www", "Restrict www-data Shell", "usermod -s /usr/sbin/nologin www-data")
        ));
        TECHNIQUE_ACTIONS.put("T1110.001", List.of(
                new RemediationOption("block_ip", "Block Attacker IP", "iptables -A INPUT -s %s -j DROP"),
                new RemediationOption("ssh_key_only", "Force SSH Key Auth", "sed -i 's/^#*PasswordAuthentication.*/PasswordAuthentication no/' /etc/ssh/sshd_config && service ssh reload")
        ));
        TECHNIQUE_ACTIONS.put("T1078", List.of(
                new RemediationOption("change_password", "Force Password Reset", "echo 'victim:$(openssl rand -base64 16)' | chpasswd"),
                new RemediationOption("lock_account", "Lock Compromised Account", "passwd -l victim")
        ));
        TECHNIQUE_ACTIONS.put("T1548.003", List.of(
                new RemediationOption("restrict_sudo", "Restrict Sudo Access", "sed -i '/www-data.*NOPASSWD/d' /etc/sudoers"),
                new RemediationOption("kill_elevated", "Kill Elevated Processes", "pkill -u root -f 'bash|sh' --newest")
        ));
        TECHNIQUE_ACTIONS.put("T1505.003", List.of(
                new RemediationOption("remove_webshell", "Remove Web Shells", "find /var/www -name '*.php' -newer /var/www/html/index.php -delete"),
                new RemediationOption("restore_webroot", "Restore Webroot Integrity", "rm -rf /var/www/html/uploads/*.php")
        ));
        TECHNIQUE_ACTIONS.put("T1053.003", List.of(
                new RemediationOption("remove_cron", "Remove Suspicious Crons", "crontab -r -u www-data ; crontab -r -u victim 2>/dev/null; rm -f /etc/cron.d/backdoor /etc/cron.d/persistence"),
                new RemediationOption("restart_cron", "Restart Cron Service", "service cron restart")
        ));
        TECHNIQUE_ACTIONS.put("T1572", List.of(
                new RemediationOption("flush_dns", "Flush DNS Cache", "systemctl restart dnsmasq"),
                new RemediationOption("block_dns_exfil", "Block DNS Exfiltration", "iptables -A OUTPUT -p udp --dport 53 ! -d 127.0.0.1 -j DROP")
        ));
        TECHNIQUE_ACTIONS.put("T1018", List.of(
                new RemediationOption("block_scanning", "Block Port Scanning", "iptables -A INPUT -p tcp --syn -m recent --name portscan --set ; iptables -A INPUT -p tcp --syn -m recent --name portscan --rcheck --seconds 5 --hitcount 10 -j DROP")
        ));
        TECHNIQUE_ACTIONS.put("T1048.003", List.of(
                new RemediationOption("block_exfil", "Block Data Exfiltration", "iptables -A OUTPUT -p tcp --dport 8888 -j DROP ; iptables -A OUTPUT -p tcp --dport 4444 -j DROP"),
                new RemediationOption("kill_transfers", "Kill Active Transfers", "pkill -f 'curl.*upload' ; pkill -f 'nc -w'")
        ));
        TECHNIQUE_ACTIONS.put("T1552.001", List.of(
                new RemediationOption("secure_files", "Secure Credential Files", "chmod 600 /var/www/html/.env /var/www/html/config.php 2>/dev/null; chmod 700 /root/.ssh 2>/dev/null")
        ));
        TECHNIQUE_ACTIONS.put("T1046", List.of(
                new RemediationOption("block_scanner", "Block Scanner IP", "iptables -A INPUT -s %s -j DROP")
        ));
        TECHNIQUE_ACTIONS.put("T1083", List.of(
                new RemediationOption("audit_find", "Restrict Find Command", "chmod 700 /usr/bin/find")
        ));
        TECHNIQUE_ACTIONS.put("T1041", List.of(
                new RemediationOption("block_c2", "Block C2 Channel", "iptables -A OUTPUT -d %s -j DROP"),
                new RemediationOption("kill_c2", "Kill C2 Connections", "pkill -f 'bash -i' ; pkill -f '/dev/tcp'")
        ));
        TECHNIQUE_ACTIONS.put("T1098", List.of(
                new RemediationOption("remove_keys", "Remove Injected SSH Keys", "rm -f /home/victim/.ssh/authorized_keys /root/.ssh/authorized_keys /var/www/.ssh/authorized_keys")
        ));
        TECHNIQUE_ACTIONS.put("T1005", List.of(
                new RemediationOption("secure_data", "Secure Sensitive Data", "chmod 600 /opt/sensitive-data/* 2>/dev/null")
        ));
    }

    public static class RemediationOption {
        private final String actionType;
        private final String label;
        private final String commandTemplate;

        public RemediationOption(String actionType, String label, String commandTemplate) {
            this.actionType = actionType;
            this.label = label;
            this.commandTemplate = commandTemplate;
        }

        public String getActionType() { return actionType; }
        public String getLabel() { return label; }
        public String getCommandTemplate() { return commandTemplate; }
    }

    private final SystemCommandExecutor commandExecutor;
    private final StreamingService streamingService;
    private final DetectionService detectionService;
    private final ScoringService scoringService;
    private final GameState gameState;
    private final List<RemediationAction> actionLog = new CopyOnWriteArrayList<>();

    public RemediationService(SystemCommandExecutor commandExecutor,
                               StreamingService streamingService,
                               DetectionService detectionService,
                               ScoringService scoringService,
                               GameState gameState) {
        this.commandExecutor = commandExecutor;
        this.streamingService = streamingService;
        this.detectionService = detectionService;
        this.scoringService = scoringService;
        this.gameState = gameState;
    }

    /**
     * Get available remediation actions for a given MITRE technique.
     */
    public List<RemediationOption> getAvailableActions(String mitreId) {
        return TECHNIQUE_ACTIONS.getOrDefault(mitreId, List.of());
    }

    /**
     * Execute a remediation action for a specific alert.
     */
    public RemediationAction remediate(String alertId, String actionType, String targetIp) {
        // Find the alert
        GameAlert alert = detectionService.getDetectionAlerts().stream()
                .filter(a -> a.getId().equals(alertId))
                .findFirst()
                .orElse(null);

        if (alert == null) {
            return new RemediationAction(
                    UUID.randomUUID().toString().substring(0, 8),
                    alertId, null, actionType, "", "FAILED", "Alert not found"
            );
        }

        String mitreId = alert.getMitreId();
        List<RemediationOption> options = getAvailableActions(mitreId);
        RemediationOption chosen = options.stream()
                .filter(o -> o.getActionType().equals(actionType))
                .findFirst()
                .orElse(null);

        if (chosen == null) {
            return new RemediationAction(
                    UUID.randomUUID().toString().substring(0, 8),
                    alertId, mitreId, actionType, "", "FAILED",
                    "No remediation action '" + actionType + "' for technique " + mitreId
            );
        }

        // Build command (substitute IP if needed)
        String command = chosen.getCommandTemplate();
        if (command.contains("%s") && targetIp != null && !targetIp.isBlank()) {
            // Validate IP format to prevent injection
            if (!targetIp.matches("^[0-9]{1,3}(\\.[0-9]{1,3}){3}$")) {
                return new RemediationAction(
                        UUID.randomUUID().toString().substring(0, 8),
                        alertId, mitreId, actionType, command, "FAILED",
                        "Invalid IP address format"
                );
            }
            command = String.format(command, targetIp);
        } else if (command.contains("%s")) {
            // Try to extract IP from alert detail
            String detail = alert.getDetail();
            String extractedIp = extractIp(detail);
            if (extractedIp != null) {
                command = String.format(command, extractedIp);
            } else {
                command = command.replace("%s", "0.0.0.0");
            }
        }

        log.info("REMEDIATION: Executing '{}' for alert {} ({})", chosen.getLabel(), alertId, mitreId);

        // Execute on victim container
        CommandResult result = commandExecutor.execute(VICTIM_CONTAINER, command);

        String status = result.success() ? "SUCCESS" : "FAILED";
        String actionId = UUID.randomUUID().toString().substring(0, 8);

        RemediationAction action = new RemediationAction(
                actionId, alertId, mitreId, actionType, command, status,
                result.success() ? result.stdout() : result.stderr()
        );

        actionLog.add(action);

        // Mark alert as resolved if remediation succeeded
        if (result.success()) {
            alert.setStatus(GameAlert.AlertStatus.RESOLVED);
            log.info("REMEDIATION SUCCESS: {} - {} resolved", actionId, alertId);
            
            // Award blue team points for successful remediation
            scoringService.awardRemediationPoints(gameState, mitreId, chosen.getLabel());
            
            // Check if IP was blocked and award additional points
            if (actionType.contains("block") || actionType.contains("ip")) {
                String ip = targetIp != null ? targetIp : extractIp(alert.getDetail());
                if (ip != null) {
                    scoringService.awardIpBlockedPoints(gameState, ip);
                    gameState.getBlockedIPs().add(ip);
                }
            }
        } else {
            alert.setStatus(GameAlert.AlertStatus.FAILED);
            log.warn("REMEDIATION FAILED: {} - {}: {}", actionId, alertId, result.stderr());
        }

        // Push remediation event to SSE
        LogEntry logEntry = new LogEntry(
                System.currentTimeMillis(),
                java.time.Instant.now().toString(),
                "REMEDIATION",
                chosen.getLabel() + " → " + status
        );
        streamingService.pushLog(logEntry);

        return action;
    }

    /**
     * Get all remediation action logs.
     */
    public List<RemediationAction> getActionLog() {
        return new ArrayList<>(actionLog);
    }

    /**
     * Get remediation stats.
     */
    public Map<String, Object> getRemediationStats() {
        long successCount = actionLog.stream().filter(a -> "SUCCESS".equals(a.getStatus())).count();
        long failedCount = actionLog.stream().filter(a -> "FAILED".equals(a.getStatus())).count();
        return Map.of(
                "totalActions", actionLog.size(),
                "successful", successCount,
                "failed", failedCount
        );
    }

    private static String extractIp(String text) {
        if (text == null) return null;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3})")
                .matcher(text);
        return m.find() ? m.group(1) : null;
    }
}
