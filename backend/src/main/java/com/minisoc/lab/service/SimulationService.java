package com.minisoc.lab.service;

import com.minisoc.lab.model.AlertItem;
import com.minisoc.lab.model.DashboardSummary;
import com.minisoc.lab.model.LogEntry;
import com.minisoc.lab.model.TodoItem;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class SimulationService {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final List<String> EVENT_POOL = List.of(
            "process_monitor.py: baseline process scan complete",
            "network_monitor.py: outbound HTTP spike detected",
            "file_monitor.py: hidden file detected in /tmp",
            "auto_kill.py: suspicious process terminated",
            "ip_block.py: C2 address blocked",
            "remove_persistence.py: cron entry removed",
            "network_monitor.py: repeated beacon sequence identified",
            "response_orchestrator: mitigation completed"
    );

    private final AtomicLong logId = new AtomicLong(1);
    private final AtomicLong alertId = new AtomicLong(1000);
    private final AtomicInteger tick = new AtomicInteger(0);

    private final List<LogEntry> logEntries = new ArrayList<>();
    private final Map<Long, AlertItem> alerts = new ConcurrentHashMap<>();
    private final Map<Long, TodoItem> todos = new ConcurrentHashMap<>();

    private volatile int eventsPerMinute = 120;
    private volatile int beaconCount = 15;
    private volatile int blocksFired = 2;
    private volatile int simulationPhase = 1;

    public SimulationService() {
        seedTodos();
        seedAlerts();
        seedLogs();
    }

    @Scheduled(fixedRate = 4000)
    public void updateSimulation() {
        int currentTick = tick.incrementAndGet();
        eventsPerMinute = 110 + (currentTick % 35);
        beaconCount = beaconCount + 1;

        if (currentTick % 3 == 0) {
            simulationPhase = (simulationPhase % 7) + 1;
        }

        String severity;
        if (currentTick % 7 == 0) {
            severity = "CRIT";
            upsertCriticalAlert();
        } else if (currentTick % 3 == 0) {
            severity = "WARN";
        } else {
            severity = "INFO";
        }

        String msg = EVENT_POOL.get(currentTick % EVENT_POOL.size());
        pushLog(severity, msg);

        if ("CRIT".equals(severity)) {
            blocksFired = blocksFired + 1;
            pushLog("OK", "auto_response: playbook fired and mitigation confirmed");
        }
    }

    public DashboardSummary getSummary() {
        long active = alerts.values().stream().filter(AlertItem::open).count();
        long critical = alerts.values().stream()
                .filter(a -> a.open() && "CRITICAL".equalsIgnoreCase(a.severity()))
                .count();

        return new DashboardSummary((int) active, (int) critical, eventsPerMinute, beaconCount, blocksFired, simulationPhase);
    }

    public List<LogEntry> getLogs() {
        return logEntries.stream()
                .sorted(Comparator.comparingLong(LogEntry::id).reversed())
                .limit(20)
                .toList();
    }

    public List<AlertItem> getAlerts() {
        return alerts.values().stream()
                .sorted(Comparator.comparingLong(AlertItem::id).reversed())
                .toList();
    }

    public List<TodoItem> getTodos() {
        return todos.values().stream()
                .sorted(Comparator.comparing(TodoItem::team).thenComparingInt(TodoItem::phase).thenComparingLong(TodoItem::id))
                .toList();
    }

    public TodoItem toggleTodo(long id) {
        TodoItem current = todos.get(id);
        if (current == null) {
            return null;
        }
        TodoItem updated = new TodoItem(current.id(), current.team(), current.phase(), current.title(), current.priority(), !current.done());
        todos.put(id, updated);
        return updated;
    }

    public void resetSimulation() {
        tick.set(0);
        eventsPerMinute = 120;
        beaconCount = 15;
        blocksFired = 2;
        simulationPhase = 1;
        logEntries.clear();
        alerts.clear();
        seedAlerts();
        seedLogs();
    }

    private void pushLog(String severity, String message) {
        synchronized (logEntries) {
            logEntries.add(new LogEntry(logId.getAndIncrement(), now(), severity, message));
            if (logEntries.size() > 200) {
                logEntries.remove(0);
            }
        }
    }

    private void upsertCriticalAlert() {
        long id = alertId.getAndIncrement();
        alerts.put(id, new AlertItem(
                id,
                "CRITICAL",
                "C2 Beaconing Detected",
                "Repeated outbound callbacks to 10.0.0.30:5000 exceeded threshold",
                "T1071.001",
                now(),
                true
        ));
    }

    private String now() {
        return LocalTime.now().format(TIME_FORMAT);
    }

    private void seedLogs() {
        pushLog("INFO", "simulation: monitors initialized");
        pushLog("WARN", "file_monitor.py: hidden artifact detected /tmp/.beacon.py");
        pushLog("CRIT", "network_monitor.py: repeated callbacks to C2 server");
        pushLog("OK", "auto_response.py: PID 4821 terminated");
    }

    private void seedAlerts() {
        long id1 = alertId.getAndIncrement();
        alerts.put(id1, new AlertItem(
                id1,
                "CRITICAL",
                "Malicious Cron Persistence",
                "Entry added: */5 * * * * python3 /tmp/.beacon.py",
                "T1053.003",
                now(),
                true
        ));

        long id2 = alertId.getAndIncrement();
        alerts.put(id2, new AlertItem(
                id2,
                "WARNING",
                "Suspicious Hidden File",
                "Hidden executable-like script created under /tmp",
                "T1105",
                now(),
                true
        ));
    }

    private void seedTodos() {
        List<TodoItem> seed = List.of(
                new TodoItem(1, "RED", 1, "Set up attacker and victim VMs", "HIGH", false),
                new TodoItem(2, "RED", 2, "Implement beacon + C2 callback", "HIGH", false),
                new TodoItem(3, "RED", 3, "Add persistence simulation", "MED", false),
                new TodoItem(4, "RED", 4, "Simulate staged file drops", "MED", false),
                new TodoItem(5, "BLUE", 1, "Set up monitoring stack", "HIGH", true),
                new TodoItem(6, "BLUE", 2, "Write process + network detections", "HIGH", false),
                new TodoItem(7, "BLUE", 3, "Implement response playbooks", "HIGH", false),
                new TodoItem(8, "BLUE", 4, "Prepare incident report output", "MED", false)
        );

        seed.forEach(item -> todos.put(item.id(), item));
    }
}
