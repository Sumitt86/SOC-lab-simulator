package com.minisoc.lab.service;

import com.minisoc.lab.executor.ActionExecutor;
import com.minisoc.lab.executor.AttackExecutor;
import com.minisoc.lab.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Core orchestrator for the adversarial cyber range.
 *
 * This service:
 *  - Receives real system events from the Python monitoring agent
 *  - Maps events to actionable alerts via EventAlertMapper
 *  - Tracks Red Team commands and scores (manual execution)
 *  - Executes Blue Team defensive actions via ActionExecutor
 *  - Manages game state (scores, threat level, timing)
 *
 * NO automated attacks — Red Team drives all offensive actions manually.
 * NO automated win/loss — game runs until manually ended.
 */
@Service
public class SimulationService {

    private static final Logger log = LoggerFactory.getLogger(SimulationService.class);
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final AttackExecutor attackExecutor;
    private final ActionExecutor actionExecutor;
    private final EventAlertMapper eventAlertMapper;

    // ===== State =====

    private final AtomicLong logId = new AtomicLong(1);
    private final List<LogEntry> logEntries = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, GameAlert> gameAlerts = new ConcurrentHashMap<>();
    private final List<SystemEvent> eventLog = new CopyOnWriteArrayList<>();
    private final Map<Long, TodoItem> todos = new ConcurrentHashMap<>();

    // Track which PIDs we know about from agent (for confirmation)
    private final Set<Long> knownMaliciousPids = ConcurrentHashMap.newKeySet();
    private final Set<String> blockedIPs = ConcurrentHashMap.newKeySet();

    private volatile GameState gameState;
    private volatile int eventsPerMinute = 0;
    private volatile int blocksFired = 0;
    private volatile int redCommandCount = 0;

    // Red Team command audit trail
    private final List<Map<String, Object>> redTeamCommandLog = Collections.synchronizedList(new ArrayList<>());

    public SimulationService(AttackExecutor attackExecutor,
                             ActionExecutor actionExecutor,
                             EventAlertMapper eventAlertMapper) {
        this.attackExecutor = attackExecutor;
        this.actionExecutor = actionExecutor;
        this.eventAlertMapper = eventAlertMapper;
        gameState = new GameState();
        seedTodos();
    }

    // ==================== THREAT SCORE (periodic recalculation) ====================

    @Scheduled(fixedRate = 5000)
    public void recalculateThreatScore() {
        if (gameState.getStatus() != GameStatus.ACTIVE) return;

        eventsPerMinute = eventLog.isEmpty() ? 0 :
                (int) (eventLog.size() * (60.0 / Math.max(1, gameState.getElapsedSeconds())));

        int score = 0;
        score += openCriticalCount() * 15;
        score += openAlertCount() * 5;
        score += knownMaliciousPids.size() * 10;
        score += gameState.isPersistenceActive() ? 20 : 0;
        score -= blockedIPs.size() * 5;

        gameState.setThreatScore(Math.max(0, Math.min(score, 100)));
    }

    // ==================== RED TEAM COMMAND EXECUTION ====================

    public Map<String, Object> executeRedTeamCommand(String container, String command) {
        if (gameState.getStatus() != GameStatus.ACTIVE) {
            return Map.of("success", false, "reason", "Game is not active");
        }

        redCommandCount++;
        pushLog("WARN", "\ud83d\udd34 RED TEAM: executing on " + container + " \u2192 " + truncateCmd(command));

        var executor = attackExecutor.getCommandExecutor();
        CommandResult result;

        boolean isAsync = command.contains("while true") || command.contains("nohup")
                || command.contains("nc -lk") || command.contains("& disown");

        if (isAsync) {
            result = executor.executeAsync(container, command);
        } else {
            result = executor.execute(container, command);
        }

        int points = scoreRedCommand(command, result.success());
        if (points > 0) {
            gameState.addRedScore(points);
        }

        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("id", redCommandCount);
        entry.put("timestamp", Instant.now().toString());
        entry.put("container", container);
        entry.put("command", command);
        entry.put("success", result.success());
        entry.put("output", result.stdout() != null ? result.stdout() : "");
        entry.put("error", result.stderr() != null ? result.stderr() : "");
        entry.put("points", points);
        entry.put("durationMs", result.durationMs());
        redTeamCommandLog.add(entry);

        if (result.success()) {
            pushLog("CRIT", "\ud83d\udd34 RED TEAM: command succeeded on " + container +
                    (points > 0 ? " (+" + points + " pts)" : ""));
        } else {
            pushLog("INFO", "\ud83d\udd34 RED TEAM: command failed on " + container);
        }

        return Map.of(
                "success", result.success(),
                "output", result.stdout() != null ? result.stdout() : "",
                "error", result.stderr() != null ? result.stderr() : "",
                "points", points,
                "durationMs", result.durationMs(),
                "async", isAsync
        );
    }

    public Map<String, Object> uploadMalware(String name, String payloadBase64,
                                              String obfuscation, String targetPath) {
        if (gameState.getStatus() != GameStatus.ACTIVE) {
            return Map.of("success", false, "reason", "Game is not active");
        }

        String victimContainer = attackExecutor.getVictimContainer();
        if (victimContainer == null || victimContainer.isBlank()) {
            return Map.of("success", false, "reason", "No victim container configured");
        }

        String filePath = targetPath != null ? targetPath : "/tmp/" + name;

        String writeCmd;
        if ("base64".equalsIgnoreCase(obfuscation)) {
            writeCmd = String.format("echo '%s' | base64 -d > %s && chmod +x %s",
                    payloadBase64, filePath, filePath);
        } else if ("hex".equalsIgnoreCase(obfuscation)) {
            writeCmd = String.format("echo '%s' | xxd -r -p > %s && chmod +x %s",
                    payloadBase64, filePath, filePath);
        } else {
            writeCmd = String.format("echo '%s' | base64 -d > %s && chmod +x %s",
                    payloadBase64, filePath, filePath);
        }

        var executor = attackExecutor.getCommandExecutor();
        CommandResult result = executor.execute(victimContainer, writeCmd);

        int points = 0;
        if (result.success()) {
            points = 5;
            gameState.addRedScore(points);
            pushLog("WARN", "\ud83d\udd34 RED TEAM: malware uploaded \u2192 " + filePath +
                    " (obfuscation: " + (obfuscation != null ? obfuscation : "none") + ")");
        }

        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("id", ++redCommandCount);
        entry.put("timestamp", Instant.now().toString());
        entry.put("container", victimContainer);
        entry.put("command", "MALWARE_UPLOAD: " + name);
        entry.put("success", result.success());
        entry.put("points", points);
        redTeamCommandLog.add(entry);

        return Map.of(
                "success", result.success(),
                "filePath", filePath,
                "obfuscation", obfuscation != null ? obfuscation : "none",
                "points", points,
                "error", result.stderr() != null ? result.stderr() : ""
        );
    }

    public List<Map<String, Object>> getRedTeamCommandLog() {
        synchronized (redTeamCommandLog) {
            return List.copyOf(redTeamCommandLog);
        }
    }

    private int scoreRedCommand(String command, boolean success) {
        if (!success) return 0;
        String cmd = command.toLowerCase();
        if (cmd.contains("nmap") || cmd.contains("ping") || cmd.contains("curl")) return 1;
        if (cmd.contains("/dev/tcp/") || cmd.contains("nc -e") || cmd.contains("nc -lk")
                || cmd.contains("while true")) return 10;
        if (cmd.contains("crontab") || cmd.contains("systemctl") || cmd.contains(".bashrc")) return 10;
        if (cmd.contains("ssh") && cmd.contains("scan")) return 5;
        if (cmd.contains("exfil") || cmd.contains(".encrypted") || cmd.contains("tar") || cmd.contains("scp")) return 20;
        return 1;
    }

    private String truncateCmd(String cmd) {
        return cmd.length() > 80 ? cmd.substring(0, 80) + "..." : cmd;
    }

    // ==================== EVENT INGESTION (from Python Agent) ====================

    /**
     * Receive a raw system event from the monitoring agent.
     * This is the real detection pipeline.
     */
    public Map<String, Object> ingestEvent(SystemEvent event) {
        if (event.getId() == null) {
            event.setId("EVT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        }
        if (event.getTimestamp() == null) {
            event.setTimestamp(Instant.now());
        }

        eventLog.add(event);

        // Log raw event
        pushLog("INFO", String.format("agent_monitor: %s detected on %s — %s (PID: %s)",
                event.getType(),
                event.getHost() != null ? event.getHost() : "unknown",
                event.getProcessName() != null ? event.getProcessName() : event.getType(),
                event.getPid() != null ? event.getPid().toString() : "N/A"));

        // Handle process termination (confirmation of Blue action)
        if (event.getType() == SystemEvent.EventType.PROCESS_TERMINATED && event.getPid() != null) {
            handleProcessTerminated(event);
            return Map.of("accepted", true, "eventId", event.getId(), "alertGenerated", false);
        }

        // Handle cron removal confirmation
        if (event.getType() == SystemEvent.EventType.CRON_REMOVED) {
            gameState.setPersistenceActive(false);
            pushLog("OK", "agent_monitor: cron persistence removed — confirmed");
            return Map.of("accepted", true, "eventId", event.getId(), "alertGenerated", false);
        }

        // Track known malicious PIDs
        if (event.getPid() != null && event.getType() == SystemEvent.EventType.PROCESS_SPAWNED) {
            knownMaliciousPids.add(event.getPid());
        }

        // Map to alert via intelligence layer
        GameAlert alert = eventAlertMapper.map(event);
        if (alert != null) {
            gameAlerts.put(alert.getId(), alert);
            gameState.setThreatScore(
                    Math.min(100, gameState.getThreatScore() + alert.getThreatImpact()));

            // Track persistence
            if (event.getType() == SystemEvent.EventType.CRON_ADDED) {
                gameState.setPersistenceActive(true);
            }

            // Track host compromises
            if (event.getHost() != null) {
                gameState.getActiveHosts().add(event.getHost());
            }

            pushLog("CRIT", String.format("ALERT: %s — %s [%s]",
                    alert.getTitle(), alert.getDetail(),
                    alert.getMitreId()));

            return Map.of(
                    "accepted", true,
                    "eventId", event.getId(),
                    "alertGenerated", true,
                    "alertId", alert.getId(),
                    "severity", alert.getSeverity()
            );
        }

        return Map.of("accepted", true, "eventId", event.getId(), "alertGenerated", false);
    }

    private void handleProcessTerminated(SystemEvent event) {
        knownMaliciousPids.remove(event.getPid());

        // Find and confirm any alerts waiting on this PID
        for (GameAlert alert : gameAlerts.values()) {
            Object killPid = alert.getActionableFields().get("killPid");
            if (killPid != null && event.getPid().equals(((Number) killPid).longValue())
                    && alert.getStatus() == GameAlert.AlertStatus.RESOLVING) {
                alert.setStatus(GameAlert.AlertStatus.RESOLVED);
                pushLog("OK", "CONFIRMED: Process " + event.getPid() + " terminated — alert " + alert.getId() + " resolved");
            }
        }

        // Check if all threats are gone → CONTAINED
        if (knownMaliciousPids.isEmpty() && openCriticalCount() == 0
                && !gameState.isPersistenceActive()) {
            gameState.setPhase(AttackPhase.CONTAINED);
            pushLog("OK", "All threats neutralized — attack CONTAINED");
        }
    }

    // ==================== BLUE TEAM ACTIONS ====================

    /**
     * Kill a process on the victim VM.
     * Optimistic: mark alert as RESOLVING immediately, confirm async via agent.
     */
    public Map<String, Object> killProcess(long pid) {
        pushLog("INFO", "blue_team: executing kill -9 " + pid);

        // Optimistic: mark related alerts as RESOLVING
        for (GameAlert alert : gameAlerts.values()) {
            Object killPid = alert.getActionableFields().get("killPid");
            if (killPid != null && pid == ((Number) killPid).longValue() && alert.isOpen()) {
                alert.setStatus(GameAlert.AlertStatus.RESOLVING);
            }
        }

        // Execute real kill command
        Map<String, Object> result = actionExecutor.killProcess(pid);
        boolean success = Boolean.TRUE.equals(result.get("success"));

        if (success) {
            gameState.addBlueScore(30);
            gameState.setThreatScore(Math.max(0, gameState.getThreatScore() - 10));
            blocksFired++;
            pushLog("OK", "blue_team: kill signal sent to PID " + pid + " — awaiting agent confirmation");
        } else {
            // Revert optimistic update
            for (GameAlert alert : gameAlerts.values()) {
                Object killPid = alert.getActionableFields().get("killPid");
                if (killPid != null && pid == ((Number) killPid).longValue()
                        && alert.getStatus() == GameAlert.AlertStatus.RESOLVING) {
                    alert.setStatus(GameAlert.AlertStatus.FAILED);
                }
            }
            pushLog("WARN", "blue_team: kill failed for PID " + pid + " — " + result.getOrDefault("details", ""));
        }

        return result;
    }

    /**
     * Block an IP on the victim VM's firewall.
     */
    public Map<String, Object> blockIP(String ip) {
        if (blockedIPs.contains(ip)) {
            return Map.of("success", false, "reason", "IP already blocked");
        }

        pushLog("INFO", "blue_team: executing iptables block on " + ip);

        Map<String, Object> result = actionExecutor.blockIp(ip);
        boolean success = Boolean.TRUE.equals(result.get("success"));

        if (success) {
            blockedIPs.add(ip);
            gameState.getBlockedIPs().add(ip);
            gameState.addBlueScore(25);
            gameState.setThreatScore(Math.max(0, gameState.getThreatScore() - 15));
            blocksFired++;
            pushLog("OK", "blue_team: IP " + ip + " blocked via iptables");

            // Resolve related network alerts
            for (GameAlert alert : gameAlerts.values()) {
                Object blockIp = alert.getActionableFields().get("blockIp");
                if (ip.equals(blockIp) && alert.isOpen()) {
                    alert.setStatus(GameAlert.AlertStatus.RESOLVED);
                }
            }
        } else {
            pushLog("WARN", "blue_team: failed to block IP " + ip + " — " + result.getOrDefault("details", ""));
        }

        return result;
    }

    /**
     * Isolate the victim host (nuclear option).
     */
    public Map<String, Object> isolateHost(String hostId) {
        if (gameState.getIsolatedHosts().contains(hostId)) {
            return Map.of("success", false, "reason", "Host already isolated");
        }

        pushLog("INFO", "blue_team: isolating host " + hostId);

        Map<String, Object> result = actionExecutor.isolateHost();
        boolean success = Boolean.TRUE.equals(result.get("success"));

        if (success) {
            gameState.getIsolatedHosts().add(hostId);
            gameState.getActiveHosts().remove(hostId);
            gameState.addBlueScore(40);
            gameState.setThreatScore(Math.max(0, gameState.getThreatScore() - 30));
            gameState.setPersistenceActive(false);
            blocksFired++;

            // Resolve all alerts for this host
            int cleared = 0;
            for (GameAlert alert : gameAlerts.values()) {
                if (alert.isOpen() && hostId.equals(alert.getHost())) {
                    alert.setStatus(GameAlert.AlertStatus.RESOLVED);
                    cleared++;
                }
            }

            knownMaliciousPids.clear();
            pushLog("OK", "blue_team: host " + hostId + " isolated — " + cleared + " alerts cleared");

            // If no active hosts remain, attack is contained
            if (gameState.getActiveHosts().isEmpty()) {
                gameState.setPhase(AttackPhase.CONTAINED);
                pushLog("OK", "blue_team: all compromised hosts isolated — attack contained");
            }

            result = new HashMap<>(result);
            result.put("alertsCleared", cleared);
        } else {
            pushLog("WARN", "blue_team: failed to isolate host — " + result.getOrDefault("details", ""));
        }

        return result;
    }

    /**
     * Remove cron persistence from the victim.
     */
    public Map<String, Object> removeCron() {
        pushLog("INFO", "blue_team: removing cron persistence");

        Map<String, Object> result = actionExecutor.removeCron();
        boolean success = Boolean.TRUE.equals(result.get("success"));

        if (success) {
            gameState.setPersistenceActive(false);
            gameState.addBlueScore(20);
            gameState.setThreatScore(Math.max(0, gameState.getThreatScore() - 10));
            blocksFired++;
            pushLog("OK", "blue_team: cron persistence removed");

            // Resolve cron alerts
            for (GameAlert alert : gameAlerts.values()) {
                if (alert.isOpen() && alert.getSourceEventType() == SystemEvent.EventType.CRON_ADDED) {
                    alert.setStatus(GameAlert.AlertStatus.RESOLVED);
                }
            }
        }

        return result;
    }

    /**
     * Resolve an alert manually (analyst decision).
     */
    public Map<String, Object> resolveAlert(String alertId) {
        GameAlert alert = gameAlerts.get(alertId);
        if (alert == null) {
            return null;
        }

        alert.setStatus(GameAlert.AlertStatus.RESOLVED);

        int points = "CRITICAL".equals(alert.getSeverity()) ? 30 : 15;
        gameState.addBlueScore(points);
        gameState.setThreatScore(Math.max(0, gameState.getThreatScore() - alert.getThreatImpact()));
        blocksFired++;

        pushLog("OK", "blue_team: resolved alert — " + alert.getTitle());

        // Check if all threats are gone → CONTAINED
        if (openCriticalCount() == 0 && knownMaliciousPids.isEmpty()
                && !gameState.isPersistenceActive()) {
            gameState.setPhase(AttackPhase.CONTAINED);
        }

        return Map.of(
                "success", true,
                "alertId", alertId,
                "points", points,
                "threatReduction", alert.getThreatImpact()
        );
    }

    // ==================== GAME CONTROL ====================

    public void startGame(Difficulty difficulty) {
        // Clean up previous game's attack artifacts
        attackExecutor.cleanup();

        gameState = new GameState();
        gameState.setDifficulty(difficulty);
        eventsPerMinute = 0;
        blocksFired = 0;
        logEntries.clear();
        gameAlerts.clear();
        eventLog.clear();
        knownMaliciousPids.clear();
        blockedIPs.clear();
        logId.set(1);

        redTeamCommandLog.clear();
        redCommandCount = 0;

        pushLog("INFO", "\ud83c\udfae Game started \u2014 Red vs Blue adversarial mode");
        pushLog("INFO", "cyber_range: Docker-based attack simulation active");
        pushLog("INFO", "\ud83d\udd34 Red Team: use /api/red-team/execute to run commands on attacker/victim");
        pushLog("INFO", "\ud83d\udd35 Blue Team: monitor alerts, scan files, analyze behavior, respond");

        if (attackExecutor.getVictimContainer() == null || attackExecutor.getVictimContainer().isBlank()) {
            pushLog("WARN", "\u26a0 No victim container configured");
        } else {
            pushLog("INFO", "cyber_range: victim \u2192 " + attackExecutor.getVictimContainer());
            pushLog("INFO", "cyber_range: attacker \u2192 " + attackExecutor.getAttackerContainer());
        }
    }

    public void resetSimulation() {
        startGame(gameState.getDifficulty());
        seedTodos();
    }

    // ==================== QUERIES ====================

    public DashboardSummary getSummary() {
        GameState gs = gameState;
        long active = gameAlerts.values().stream().filter(GameAlert::isOpen).count();
        long critical = gameAlerts.values().stream()
                .filter(a -> a.isOpen() && "CRITICAL".equalsIgnoreCase(a.getSeverity()))
                .count();

        return new DashboardSummary(
                (int) active,
                (int) critical,
                eventsPerMinute,
                knownMaliciousPids.size(),
                blocksFired,
                gs.getPhase().getLevel(),
                gs.getPhase().name(),
                gs.getPhase().getDisplayName(),
                gs.getThreatScore(),
                gs.getThreatLevel(),
                gs.getStatus().name(),
                gs.getRedScore(),
                gs.getBlueScore(),
                gs.getElapsedSeconds(),
                gs.isPersistenceActive(),
                gs.getActiveHosts().size(),
                gs.getDifficulty().name(),
                gs.getWinReason()
        );
    }

    public List<LogEntry> getLogs() {
        synchronized (logEntries) {
            return logEntries.stream()
                    .sorted(Comparator.comparingLong(LogEntry::id).reversed())
                    .limit(50)
                    .toList();
        }
    }

    /**
     * Get alerts in the format the frontend expects.
     * Maps GameAlerts to the existing AlertItem record for backward compatibility.
     */
    public List<AlertItem> getAlerts() {
        return gameAlerts.values().stream()
                .sorted((a, b) -> b.getTimestamp().compareTo(a.getTimestamp()))
                .map(this::toAlertItem)
                .toList();
    }

    public List<SystemEvent> getEvents() {
        return List.copyOf(eventLog);
    }

    public List<SystemEvent> getEventsByHost(String host) {
        return eventLog.stream()
                .filter(e -> host.equals(e.getHost()))
                .toList();
    }

    public List<TodoItem> getTodos() {
        return todos.values().stream()
                .sorted(Comparator.comparing(TodoItem::team)
                        .thenComparingInt(TodoItem::phase)
                        .thenComparingLong(TodoItem::id))
                .toList();
    }

    public TodoItem toggleTodo(long id) {
        TodoItem current = todos.get(id);
        if (current == null) return null;
        TodoItem updated = new TodoItem(current.id(), current.team(), current.phase(),
                current.title(), current.priority(), !current.done());
        todos.put(id, updated);
        return updated;
    }

    /**
     * Get available Blue Team actions based on real system state.
     */
    public Map<String, Object> getAvailableActions() {
        GameState gs = gameState;

        // Blockable IPs: from alert actionable fields
        Set<String> blockableIPSet = new LinkedHashSet<>();
        for (GameAlert alert : gameAlerts.values()) {
            if (!alert.isOpen()) continue;
            Object blockIp = alert.getActionableFields().get("blockIp");
            if (blockIp instanceof String ip && !blockedIPs.contains(ip)) {
                blockableIPSet.add(ip);
            }
        }

        // Killable PIDs: from alert actionable fields
        List<Map<String, Object>> killableProcesses = new ArrayList<>();
        for (GameAlert alert : gameAlerts.values()) {
            if (!alert.isOpen()) continue;
            Object killPid = alert.getActionableFields().get("killPid");
            if (killPid != null) {
                killableProcesses.add(Map.of(
                        "pid", killPid,
                        "alertId", alert.getId(),
                        "title", alert.getTitle(),
                        "host", alert.getHost() != null ? alert.getHost() : ""
                ));
            }
        }

        // Isolatable hosts
        List<String> isolatableHosts = new ArrayList<>(gs.getActiveHosts());

        // Resolvable alerts
        List<Map<String, Object>> resolvableAlerts = gameAlerts.values().stream()
                .filter(GameAlert::isOpen)
                .map(a -> Map.<String, Object>of(
                        "id", a.getId(),
                        "severity", a.getSeverity(),
                        "title", a.getTitle(),
                        "host", a.getHost() != null ? a.getHost() : "",
                        "actionableFields", a.getActionableFields()
                ))
                .toList();

        // Is cron persistence active?
        boolean canRemoveCron = gs.isPersistenceActive();

        return Map.of(
                "blockableIPs", List.copyOf(blockableIPSet),
                "killableProcesses", killableProcesses,
                "isolatableHosts", isolatableHosts,
                "resolvableAlerts", resolvableAlerts,
                "canRemoveCron", canRemoveCron
        );
    }

    public GameState getGameState() {
        return gameState;
    }

    // ==================== HELPERS ====================

    public void pushLog(String severity, String message) {
        synchronized (logEntries) {
            logEntries.add(new LogEntry(logId.getAndIncrement(), now(), severity, message));
            if (logEntries.size() > 200) {
                logEntries.remove(0);
            }
        }
    }

    private String now() {
        return LocalTime.now().format(TIME_FORMAT);
    }

    private long openCriticalCount() {
        return gameAlerts.values().stream()
                .filter(a -> a.isOpen() && "CRITICAL".equalsIgnoreCase(a.getSeverity()))
                .count();
    }

    private long openAlertCount() {
        return gameAlerts.values().stream().filter(GameAlert::isOpen).count();
    }

    private AlertItem toAlertItem(GameAlert alert) {
        return new AlertItem(
                alert.getId().hashCode() & 0xFFFFL,
                alert.getSeverity(),
                alert.getTitle(),
                alert.getDetail(),
                alert.getMitreId(),
                alert.getTimestamp().toString(),
                alert.isOpen(),
                alert.getHost()
        );
    }

    // ==================== BLUE TEAM INVESTIGATION ====================

    /**
     * Get live process list from victim container.
     */
    public Map<String, Object> getProcessList() {
        var executor = attackExecutor.getCommandExecutor();
        String victim = attackExecutor.getVictimContainer();
        if (victim == null || victim.isBlank()) {
            return Map.of("success", false, "reason", "No victim container");
        }
        CommandResult result = executor.execute(victim, "ps aux --sort=-%cpu");
        return Map.of(
                "success", result.success(),
                "output", result.stdout() != null ? result.stdout() : "",
                "error", result.stderr() != null ? result.stderr() : ""
        );
    }

    /**
     * Get live network connections from victim container.
     */
    public Map<String, Object> getNetworkConnections() {
        var executor = attackExecutor.getCommandExecutor();
        String victim = attackExecutor.getVictimContainer();
        if (victim == null || victim.isBlank()) {
            return Map.of("success", false, "reason", "No victim container");
        }
        CommandResult result = executor.execute(victim, "ss -tulnp 2>/dev/null || netstat -tulnp 2>/dev/null");
        return Map.of(
                "success", result.success(),
                "output", result.stdout() != null ? result.stdout() : "",
                "error", result.stderr() != null ? result.stderr() : ""
        );
    }

    /**
     * Get files in /tmp from victim container.
     */
    public Map<String, Object> getFileList() {
        var executor = attackExecutor.getCommandExecutor();
        String victim = attackExecutor.getVictimContainer();
        if (victim == null || victim.isBlank()) {
            return Map.of("success", false, "reason", "No victim container");
        }
        CommandResult result = executor.execute(victim,
                "find /tmp /var/tmp /dev/shm -type f -ls 2>/dev/null | head -50");
        return Map.of(
                "success", result.success(),
                "output", result.stdout() != null ? result.stdout() : "",
                "error", result.stderr() != null ? result.stderr() : ""
        );
    }

    /**
     * Scan a file on the victim for known malware signatures.
     */
    public Map<String, Object> scanFile(String filePath) {
        var executor = attackExecutor.getCommandExecutor();
        String victim = attackExecutor.getVictimContainer();
        if (victim == null || victim.isBlank()) {
            return Map.of("success", false, "reason", "No victim container");
        }

        // Safety: restrict to /tmp and /var/tmp
        if (!filePath.startsWith("/tmp/") && !filePath.startsWith("/var/tmp/")) {
            return Map.of("success", false, "reason", "Can only scan files under /tmp/ or /var/tmp/");
        }

        // Read file content + compute hash
        CommandResult catResult = executor.execute(victim, "cat " + filePath + " 2>/dev/null");
        CommandResult hashResult = executor.execute(victim, "sha256sum " + filePath + " 2>/dev/null");
        CommandResult fileResult = executor.execute(victim, "file " + filePath + " 2>/dev/null");

        if (!catResult.success()) {
            return Map.of("success", false, "reason", "File not found or unreadable");
        }

        String content = catResult.stdout();
        String hash = hashResult.success() ? hashResult.stdout().split("\\s+")[0] : "unknown";
        String fileType = fileResult.success() ? fileResult.stdout() : "unknown";

        // Signature-based analysis
        List<Map<String, Object>> threats = new ArrayList<>();
        int threatScore = 0;

        // Check for known malware patterns (YARA-like string matching)
        if (content.contains("/dev/tcp/") || content.contains("bash -i >&")) {
            threats.add(Map.of(
                    "type", "TROJAN",
                    "name", "Trojan.ReverseShell.Bash",
                    "severity", "CRITICAL",
                    "rule", "reverse_shell_bash",
                    "detail", "Bash reverse shell pattern detected"
            ));
            threatScore += 90;
        }
        if (content.contains("nc ") && (content.contains("-e /bin") || content.contains("-c /bin"))) {
            threats.add(Map.of(
                    "type", "TROJAN",
                    "name", "Trojan.Netcat.ReverseShell",
                    "severity", "CRITICAL",
                    "rule", "netcat_reverse_shell",
                    "detail", "Netcat reverse shell with -e/-c flag"
            ));
            threatScore += 85;
        }
        if (content.contains("while true") && content.contains("nc ")) {
            threats.add(Map.of(
                    "type", "C2_BEACON",
                    "name", "Beacon.Loop.Generic",
                    "severity", "HIGH",
                    "rule", "c2_beacon_loop",
                    "detail", "Persistent C2 beacon loop detected"
            ));
            threatScore += 70;
        }
        if (content.contains("crontab") || content.contains("*/")) {
            threats.add(Map.of(
                    "type", "PERSISTENCE",
                    "name", "Persist.Cron.Generic",
                    "severity", "HIGH",
                    "rule", "cron_persistence",
                    "detail", "Cron-based persistence mechanism"
            ));
            threatScore += 60;
        }
        if (content.contains(".encrypted") || content.contains("RANSOM")) {
            threats.add(Map.of(
                    "type", "RANSOMWARE",
                    "name", "Ransom.FileEncrypt.Generic",
                    "severity", "CRITICAL",
                    "rule", "ransomware_simulation",
                    "detail", "Ransomware-like file encryption pattern"
            ));
            threatScore += 95;
        }
        // Check for obfuscated payloads
        if (content.contains("base64 -d") || content.contains("eval(") || content.contains("exec(")) {
            threats.add(Map.of(
                    "type", "OBFUSCATED",
                    "name", "Obfuscated.Payload.Generic",
                    "severity", "MEDIUM",
                    "rule", "obfuscated_payload",
                    "detail", "Obfuscated/encoded payload execution"
            ));
            threatScore += 40;
        }

        // High entropy check (packed/encoded content)
        double entropy = calculateEntropy(content);
        boolean highEntropy = entropy > 5.5;
        if (highEntropy && threats.isEmpty()) {
            threats.add(Map.of(
                    "type", "PACKED",
                    "name", "Packed.HighEntropy",
                    "severity", "MEDIUM",
                    "rule", "high_entropy_file",
                    "detail", String.format("Suspiciously high entropy (%.2f) — possible packed/encoded malware", entropy)
            ));
            threatScore += 35;
        }

        String verdict = threats.isEmpty() ? "CLEAN" :
                threatScore >= 70 ? "MALICIOUS" : "SUSPICIOUS";

        // Score Blue Team for scanning
        if (!threats.isEmpty()) {
            gameState.addBlueScore(20);
            pushLog("OK", "\ud83d\udd35 BLUE TEAM: file scan detected " + threats.size() +
                    " threat(s) in " + filePath + " (+20 pts)");
        } else {
            pushLog("INFO", "\ud83d\udd35 BLUE TEAM: file scan on " + filePath + " \u2014 clean");
        }

        return Map.of(
                "success", true,
                "filePath", filePath,
                "hash", hash,
                "fileType", fileType,
                "entropy", entropy,
                "highEntropy", highEntropy,
                "verdict", verdict,
                "threatScore", Math.min(threatScore, 100),
                "threats", threats
        );
    }

    /**
     * Behavioral analysis: correlate recent events to detect anomalies.
     */
    public Map<String, Object> behavioralAnalysis(int lastMinutes) {
        Instant cutoff = Instant.now().minusSeconds(lastMinutes * 60L);

        List<SystemEvent> recentEvents = eventLog.stream()
                .filter(e -> e.getTimestamp().isAfter(cutoff))
                .toList();

        List<Map<String, Object>> anomalies = new ArrayList<>();
        int combinedScore = 0;

        // Pattern 1: C2 Communication (outbound connections to suspicious ports)
        long suspiciousNetConns = recentEvents.stream()
                .filter(e -> e.getType() == SystemEvent.EventType.NETWORK_CONNECTION)
                .filter(e -> e.getRemotePort() != null && (e.getRemotePort() == 4444
                        || e.getRemotePort() == 5555 || e.getRemotePort() == 9999
                        || e.getRemotePort() == 1234 || e.getRemotePort() == 8888))
                .count();
        if (suspiciousNetConns > 0) {
            anomalies.add(Map.of(
                    "type", "C2_COMMUNICATION",
                    "description", "Outbound connections to known C2 ports detected",
                    "count", suspiciousNetConns,
                    "score", 85,
                    "rule", "C2_PORT_PATTERN"
            ));
            combinedScore += 85;
        }

        // Pattern 2: Rapid process spawning (potential fork bomb or spray)
        long newProcs = recentEvents.stream()
                .filter(e -> e.getType() == SystemEvent.EventType.PROCESS_SPAWNED)
                .count();
        if (newProcs > 20) {
            anomalies.add(Map.of(
                    "type", "RAPID_PROCESS_SPAWN",
                    "description", "Abnormal process spawning rate: " + newProcs + " in " + lastMinutes + " min",
                    "count", newProcs,
                    "score", 70,
                    "rule", "PROCESS_SPRAY_PATTERN"
            ));
            combinedScore += 70;
        }

        // Pattern 3: File system abuse (many new files in /tmp)
        long newFiles = recentEvents.stream()
                .filter(e -> e.getType() == SystemEvent.EventType.FILE_CREATED)
                .count();
        if (newFiles > 10) {
            anomalies.add(Map.of(
                    "type", "RAPID_FILE_CREATION",
                    "description", "Rapid file creation in monitored dirs: " + newFiles + " files",
                    "count", newFiles,
                    "score", 55,
                    "rule", "FILE_CREATION_BURST"
            ));
            combinedScore += 55;
        }

        // Pattern 4: Cron persistence
        long cronAdds = recentEvents.stream()
                .filter(e -> e.getType() == SystemEvent.EventType.CRON_ADDED)
                .count();
        if (cronAdds > 0) {
            anomalies.add(Map.of(
                    "type", "PERSISTENCE_INSTALLED",
                    "description", "Cron job(s) added: " + cronAdds,
                    "count", cronAdds,
                    "score", 75,
                    "rule", "CRON_PERSISTENCE_PATTERN"
            ));
            combinedScore += 75;
        }

        // Pattern 5: Suspicious process names
        long suspiciousProcs = recentEvents.stream()
                .filter(e -> e.getType() == SystemEvent.EventType.PROCESS_SPAWNED)
                .filter(e -> {
                    String name = e.getProcessName() != null ? e.getProcessName().toLowerCase() : "";
                    String cmdline = e.getCmdline() != null ? e.getCmdline().toLowerCase() : "";
                    return name.matches(".*(nc|ncat|netcat|nmap|socat|python|perl|ruby).*")
                            || cmdline.contains("/dev/tcp") || cmdline.contains("reverse")
                            || cmdline.contains("beacon");
                })
                .count();
        if (suspiciousProcs > 0) {
            anomalies.add(Map.of(
                    "type", "SUSPICIOUS_PROCESSES",
                    "description", "Known attack tool processes detected: " + suspiciousProcs,
                    "count", suspiciousProcs,
                    "score", 80,
                    "rule", "ATTACK_TOOL_PATTERN"
            ));
            combinedScore += 80;
        }

        String recommendation;
        if (combinedScore >= 150) recommendation = "ISOLATE_IMMEDIATELY";
        else if (combinedScore >= 80) recommendation = "INVESTIGATE_AND_RESPOND";
        else if (combinedScore >= 30) recommendation = "MONITOR_CLOSELY";
        else recommendation = "NO_ACTION_NEEDED";

        // Score Blue Team for analysis
        if (!anomalies.isEmpty()) {
            gameState.addBlueScore(15);
            pushLog("OK", "\ud83d\udd35 BLUE TEAM: behavioral analysis found " + anomalies.size() +
                    " anomaly patterns (+15 pts)");
        }

        return Map.of(
                "success", true,
                "analyzedMinutes", lastMinutes,
                "eventCount", recentEvents.size(),
                "anomalies", anomalies,
                "combinedThreatScore", Math.min(combinedScore, 100),
                "recommendation", recommendation
        );
    }

    private double calculateEntropy(String data) {
        if (data == null || data.isEmpty()) return 0;
        int[] freq = new int[256];
        for (char c : data.toCharArray()) freq[c & 0xFF]++;
        double entropy = 0;
        int len = data.length();
        for (int f : freq) {
            if (f == 0) continue;
            double p = (double) f / len;
            entropy -= p * (Math.log(p) / Math.log(2));
        }
        return entropy;
    }

    private void seedTodos() {
        todos.clear();
        List<TodoItem> seed = List.of(
                new TodoItem(1, "RED", 1, "Reconnaissance: scan victim ports", "HIGH", false),
                new TodoItem(2, "RED", 2, "Initial access: establish C2 beacon", "HIGH", false),
                new TodoItem(3, "RED", 3, "Persistence: install cron backdoor", "MED", false),
                new TodoItem(4, "RED", 4, "Upload obfuscated malware payload", "MED", false),
                new TodoItem(5, "RED", 5, "Exfiltrate sensitive data", "HIGH", false),
                new TodoItem(6, "BLUE", 1, "Monitor processes for anomalies", "HIGH", false),
                new TodoItem(7, "BLUE", 2, "Scan suspicious files (signature)", "HIGH", false),
                new TodoItem(8, "BLUE", 3, "Run behavioral analysis", "HIGH", false),
                new TodoItem(9, "BLUE", 4, "Kill malicious processes", "MED", false),
                new TodoItem(10, "BLUE", 5, "Block attacker IPs + clean up", "MED", false)
        );
        seed.forEach(todo -> todos.put(todo.id(), todo));
    }
}
