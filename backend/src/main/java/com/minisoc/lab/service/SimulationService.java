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
 * Core game loop orchestrator for the VM-based cyber range.
 *
 * This service:
 *  - Receives real system events from the Python monitoring agent
 *  - Maps events to actionable alerts via EventAlertMapper
 *  - Triggers attacks on the victim VM via AttackExecutor
 *  - Executes Blue Team actions via ActionExecutor
 *  - Manages game state (phase, scores, threat, win/loss)
 *
 * The game loop no longer generates fake events. Instead:
 *  - RED: AttackExecutor runs real commands on the victim
 *  - BLUE: ActionExecutor runs real defensive commands
 *  - DETECTION: Python agent posts real events via /api/events
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
    private volatile boolean phaseAttackLaunched = false;
    private int tickCount = 0;

    public SimulationService(AttackExecutor attackExecutor,
                             ActionExecutor actionExecutor,
                             EventAlertMapper eventAlertMapper) {
        this.attackExecutor = attackExecutor;
        this.actionExecutor = actionExecutor;
        this.eventAlertMapper = eventAlertMapper;
        gameState = new GameState();
        seedTodos();
    }

    // ==================== GAME LOOP ====================

    @Scheduled(fixedRate = 4000)
    public void tick() {
        if (gameState.getStatus() != GameStatus.ACTIVE) return;

        tickCount++;
        eventsPerMinute = eventLog.size() * (60 / Math.max(1, (int) gameState.getElapsedSeconds()));

        triggerPhaseAttackIfNeeded();
        evaluateThreatScore();
        checkPhaseEscalation();
        checkWinLoss();
    }

    /**
     * Trigger the attack for the current phase if not already launched.
     * Attacks are real SSH commands to the victim VM.
     */
    private void triggerPhaseAttackIfNeeded() {
        if (phaseAttackLaunched) return;

        GameState gs = gameState;
        AttackPhase phase = gs.getPhase();

        if (phase == AttackPhase.CONTAINED) return;

        // Launch the attack for this phase
        Map<String, Object> result = attackExecutor.executePhaseAttack(phase);
        phaseAttackLaunched = true;

        boolean success = Boolean.TRUE.equals(result.get("success"));
        if (success) {
            pushLog("CRIT", "⚠ RED TEAM: " + phase.getDisplayName() + " attack launched on " +
                    (attackExecutor.getVictimIp() != null ? attackExecutor.getVictimIp() : "victim"));
            gs.addRedScore(10);
        } else {
            pushLog("WARN", "RED TEAM: " + phase.getDisplayName() + " attack attempted but failed — " +
                    result.getOrDefault("reason", "unknown"));
        }
    }

    private void evaluateThreatScore() {
        GameState gs = gameState;

        // Threat score is based on real system state:
        // - Number of open critical alerts (from real events)
        // - Known malicious PIDs still running
        // - Phase level
        // - Blocked IPs vs total C2s
        int score = 0;
        score += openCriticalCount() * 15;
        score += openAlertCount() * 5;
        score += knownMaliciousPids.size() * 10;
        score += gs.isPersistenceActive() ? 20 : 0;
        score += (gs.getPhase().getLevel() - 1) * 10;
        score -= blockedIPs.size() * 5;

        gs.setThreatScore(Math.max(0, Math.min(score, 100)));
    }

    private void checkPhaseEscalation() {
        GameState gs = gameState;
        long phaseSeconds = gs.getPhaseElapsedSeconds();
        int threshold = gs.getDifficulty().getEscalationThresholdSeconds();

        boolean shouldEscalate = switch (gs.getPhase()) {
            case INITIAL_ACCESS ->
                    openCriticalCount() > 0 && phaseSeconds > threshold;
            case PERSISTENCE ->
                    gs.isPersistenceActive() && phaseSeconds > threshold;
            case LATERAL_MOVEMENT ->
                    gs.getThreatScore() > 75 || phaseSeconds > threshold * 2;
            default -> false;
        };

        if (shouldEscalate) {
            AttackPhase nextPhase = gs.getPhase().next();
            gs.setPhase(nextPhase);
            gs.addRedScore(100);
            phaseAttackLaunched = false; // allow next phase attack
            pushLog("CRIT", "⚠ PHASE ESCALATED → " + nextPhase.getDisplayName());
        }
    }

    private void checkWinLoss() {
        GameState gs = gameState;
        long elapsed = gs.getElapsedSeconds();

        // RED WINS: reached exfiltration
        if (gs.getPhase() == AttackPhase.EXFILTRATION) {
            gs.setStatus(GameStatus.RED_WIN);
            gs.setWinReason("Red Team reached Exfiltration phase — data breach!");
            pushLog("CRIT", "🔴 GAME OVER — Red Team wins! Data exfiltration successful.");
            return;
        }

        // RED WINS: threat overwhelmed
        if (gs.getThreatScore() > 90) {
            gs.setStatus(GameStatus.RED_WIN);
            gs.setWinReason("Threat score exceeded 90 — system overwhelmed!");
            pushLog("CRIT", "🔴 GAME OVER — Red Team wins! System overwhelmed.");
            return;
        }

        // BLUE WINS: survived 60s + system under control
        if (elapsed >= 60 && gs.getThreatScore() < 40
                && openCriticalCount() == 0
                && gs.getPhase().getLevel() <= 3) {
            gs.setStatus(GameStatus.BLUE_WIN);
            gs.setWinReason("System contained for 60+ seconds with low threat.");
            pushLog("OK", "🔵 GAME OVER — Blue Team wins! Threat contained.");
            attackExecutor.cleanup();
            return;
        }

        // BLUE WINS: fully contained
        if (gs.getPhase() == AttackPhase.CONTAINED) {
            gs.setStatus(GameStatus.BLUE_WIN);
            gs.setWinReason("All attack vectors neutralized.");
            pushLog("OK", "🔵 GAME OVER — Blue Team wins! All threats eliminated.");
            attackExecutor.cleanup();
            return;
        }

        // DRAW: too long
        if (elapsed > 180) {
            gs.setStatus(GameStatus.DRAW);
            gs.setWinReason("Time expired — stalemate after 180 seconds.");
            pushLog("INFO", "⏱ GAME OVER — Draw. Time expired.");
            attackExecutor.cleanup();
        }
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
        tickCount = 0;
        eventsPerMinute = 0;
        blocksFired = 0;
        phaseAttackLaunched = false;
        logEntries.clear();
        gameAlerts.clear();
        eventLog.clear();
        knownMaliciousPids.clear();
        blockedIPs.clear();
        logId.set(1);

        pushLog("INFO", "🎮 Game started — Difficulty: " + difficulty.name());
        pushLog("INFO", "cyber_range: VM-based attack simulation active");

        if (attackExecutor.getVictimIp() == null || attackExecutor.getVictimIp().isBlank()) {
            pushLog("WARN", "⚠ No victim VM configured — set cyber-range.victim.ip in application.properties");
            pushLog("INFO", "Running in MONITOR-ONLY mode — agent events will still be processed");
        } else {
            pushLog("INFO", "cyber_range: target victim → " + attackExecutor.getVictimIp());
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

    private void seedTodos() {
        todos.clear();
        List<TodoItem> seed = List.of(
                new TodoItem(1, "RED", 1, "Set up attacker and victim VMs", "HIGH", false),
                new TodoItem(2, "RED", 2, "Implement beacon + C2 callback", "HIGH", false),
                new TodoItem(3, "RED", 3, "Add persistence simulation", "MED", false),
                new TodoItem(4, "RED", 4, "Simulate staged file drops", "MED", false),
                new TodoItem(5, "BLUE", 1, "Set up monitoring agent on victim", "HIGH", false),
                new TodoItem(6, "BLUE", 2, "Write process + network detections", "HIGH", false),
                new TodoItem(7, "BLUE", 3, "Implement response playbooks", "HIGH", false),
                new TodoItem(8, "BLUE", 4, "Prepare incident report output", "MED", false)
        );
        seed.forEach(todo -> todos.put(todo.id(), todo));
    }
}
