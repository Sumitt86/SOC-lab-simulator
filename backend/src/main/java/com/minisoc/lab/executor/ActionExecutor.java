package com.minisoc.lab.executor;

import com.minisoc.lab.model.CommandResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Executes Blue Team defensive actions on the victim container via docker exec.
 *
 * Each action maps to a real system command:
 *   kill-process  → kill -9 <pid>
 *   block-ip      → iptables -A INPUT -s <ip> -j DROP
 *   isolate-host  → iptables drop all + kill non-system processes
 *   remove-cron   → crontab -r (or specific entry removal)
 *   remove-file   → rm <file>
 */
@Service
public class ActionExecutor {

    private static final Logger log = LoggerFactory.getLogger(ActionExecutor.class);

    private final SystemCommandExecutor commandExecutor;

    @Value("${cyber-range.victim.container:soc-victim}")
    private String victimContainer;

    public ActionExecutor(SystemCommandExecutor commandExecutor) {
        this.commandExecutor = commandExecutor;
    }

    /**
     * Kill a process by PID on the victim.
     */
    public Map<String, Object> killProcess(long pid) {
        if (!hasVictim()) return noVictim();

        log.info("BLUE ACTION: kill process PID {} on {}", pid, victimContainer);

        // Verify process exists first, then kill
        CommandResult verifyResult = commandExecutor.execute(victimContainer,
                "ps -p " + pid + " -o pid,comm --no-headers 2>/dev/null");

        if (!verifyResult.success() || verifyResult.stdout().isBlank()) {
            log.warn("Process {} not found on {}", pid, victimContainer);
            return Map.of(
                    "success", false,
                    "action", "kill_process",
                    "pid", pid,
                    "reason", "Process not found (may have already terminated)"
            );
        }

        CommandResult killResult = commandExecutor.execute(victimContainer, "kill -9 " + pid);

        return Map.of(
                "success", killResult.success(),
                "action", "kill_process",
                "pid", pid,
                "host", victimContainer,
                "details", killResult.success() ? "Process " + pid + " terminated" : killResult.stderr()
        );
    }

    /**
     * Block an IP address using iptables on the victim.
     */
    public Map<String, Object> blockIp(String ip) {
        if (!hasVictim()) return noVictim();

        log.info("BLUE ACTION: block IP {} on {}", ip, victimContainer);

        // Add iptables rule (both INPUT and OUTPUT) + kill processes referencing this IP
        String command = String.format(
                "iptables -A INPUT -s %s -j DROP && iptables -A OUTPUT -d %s -j DROP && pkill -f '%s' 2>/dev/null; true",
                ip, ip, ip
        );
        CommandResult result = commandExecutor.execute(victimContainer, command);

        return Map.of(
                "success", result.success(),
                "action", "block_ip",
                "ip", ip,
                "host", victimContainer,
                "details", result.success()
                        ? "IP " + ip + " blocked (INPUT + OUTPUT) + beacon processes killed"
                        : result.stderr()
        );
    }

    /**
     * Isolate a host by blocking all network traffic and killing suspicious processes.
     */
    public Map<String, Object> isolateHost() {
        if (!hasVictim()) return noVictim();

        log.info("BLUE ACTION: isolate host {}", victimContainer);

        // Kill all suspicious processes, flush iptables, block everything
        // No need to preserve SSH in Docker — we use docker exec
        String command = "pkill -f 'while true.*beacon' 2>/dev/null; " +
                "pkill -f '.beacon.sh' 2>/dev/null; " +
                "pkill -f 'nc ' 2>/dev/null; " +
                "iptables -F && " +
                "iptables -A INPUT -j DROP && " +
                "iptables -A OUTPUT -j DROP";

        CommandResult result = commandExecutor.execute(victimContainer, command);

        return Map.of(
                "success", result.success(),
                "action", "isolate_host",
                "host", victimContainer,
                "details", result.success()
                        ? "Host isolated — all traffic blocked, suspicious processes killed"
                        : result.stderr()
        );
    }

    /**
     * Remove cron persistence from the victim.
     */
    public Map<String, Object> removeCron() {
        if (!hasVictim()) return noVictim();

        log.info("BLUE ACTION: remove cron on {}", victimContainer);

        // Remove all crontab entries and the beacon script
        String command = "crontab -r 2>/dev/null; rm -f /tmp/.beacon.sh 2>/dev/null; echo done";
        CommandResult result = commandExecutor.execute(victimContainer, command);

        return Map.of(
                "success", result.success(),
                "action", "remove_cron",
                "host", victimContainer,
                "details", result.success()
                        ? "Cron jobs cleared, beacon script removed"
                        : result.stderr()
        );
    }

    /**
     * Remove a specific file from the victim.
     */
    public Map<String, Object> removeFile(String filePath) {
        if (!hasVictim()) return noVictim();

        // Validate: only allow removal from /tmp/ (safety)
        if (filePath == null || !filePath.startsWith("/tmp/")) {
            return Map.of("success", false, "reason", "Can only remove files under /tmp/");
        }

        log.info("BLUE ACTION: remove file {} on {}", filePath, victimContainer);

        CommandResult result = commandExecutor.execute(victimContainer, "rm -f " + filePath);

        return Map.of(
                "success", result.success(),
                "action", "remove_file",
                "filePath", filePath,
                "host", victimContainer,
                "details", result.success() ? "File removed" : result.stderr()
        );
    }

    private boolean hasVictim() {
        return victimContainer != null && !victimContainer.isBlank();
    }

    private Map<String, Object> noVictim() {
        return Map.of("success", false, "reason", "No victim container configured (cyber-range.victim.container)");
    }
}
