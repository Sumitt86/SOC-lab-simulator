package com.minisoc.lab.controller;

import com.minisoc.lab.model.*;
import com.minisoc.lab.service.SimulationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class SimulationController {

    private final SimulationService simulationService;

    public SimulationController(SimulationService simulationService) {
        this.simulationService = simulationService;
    }

    // ==================== HEALTH & DASHBOARD ====================

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }

    @GetMapping("/dashboard/summary")
    public DashboardSummary summary() {
        return simulationService.getSummary();
    }

    @GetMapping("/dashboard/logs")
    public List<LogEntry> logs() {
        return simulationService.getLogs();
    }

    @GetMapping("/dashboard/alerts")
    public List<AlertItem> alerts() {
        return simulationService.getAlerts();
    }

    // ==================== TODOS ====================

    @GetMapping("/todos")
    public List<TodoItem> todos() {
        return simulationService.getTodos();
    }

    @PatchMapping("/todos/{id}/toggle")
    public ResponseEntity<TodoItem> toggleTodo(@PathVariable long id) {
        TodoItem updated = simulationService.toggleTodo(id);
        if (updated == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(updated);
    }

    // ==================== GAME CONTROL ====================

    @PostMapping("/simulation/reset")
    public ResponseEntity<Map<String, String>> reset() {
        simulationService.resetSimulation();
        return ResponseEntity.ok(Map.of("message", "Simulation reset"));
    }

    @PostMapping("/simulation/start")
    public ResponseEntity<Map<String, String>> startGame(@RequestBody(required = false) Map<String, String> body) {
        String difficultyStr = (body != null && body.containsKey("difficulty"))
                ? body.get("difficulty") : "MEDIUM";
        Difficulty difficulty;
        try {
            difficulty = Difficulty.valueOf(difficultyStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            difficulty = Difficulty.MEDIUM;
        }
        simulationService.startGame(difficulty);
        return ResponseEntity.ok(Map.of(
                "message", "Game started",
                "difficulty", difficulty.name()
        ));
    }

    // ==================== EVENT INGESTION (from Python Agent) ====================

    @PostMapping("/events")
    public ResponseEntity<Map<String, Object>> ingestEvent(@RequestBody SystemEvent event) {
        Map<String, Object> result = simulationService.ingestEvent(event);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(result);
    }

    @GetMapping("/events")
    public List<SystemEvent> getEvents(@RequestParam(required = false) String host) {
        if (host != null && !host.isBlank()) {
            return simulationService.getEventsByHost(host);
        }
        return simulationService.getEvents();
    }

    // ==================== BLUE TEAM ACTIONS ====================

    @PostMapping("/alerts/{alertId}/resolve")
    public ResponseEntity<Map<String, Object>> resolveAlert(@PathVariable String alertId) {
        Map<String, Object> result = simulationService.resolveAlert(alertId);
        if (result == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping("/actions/kill-process")
    public ResponseEntity<Map<String, Object>> killProcess(@RequestBody Map<String, Object> body) {
        Object pidObj = body.get("pid");
        if (pidObj == null) {
            return ResponseEntity.badRequest().body(Map.of("error", (Object) "PID required"));
        }
        long pid = ((Number) pidObj).longValue();
        return ResponseEntity.ok(simulationService.killProcess(pid));
    }

    @PostMapping("/actions/block-ip")
    public ResponseEntity<Map<String, Object>> blockIP(@RequestBody Map<String, String> body) {
        String ip = body.get("ip");
        if (ip == null || ip.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", (Object) "IP address required"));
        }
        return ResponseEntity.ok(simulationService.blockIP(ip));
    }

    @PostMapping("/actions/isolate-host")
    public ResponseEntity<Map<String, Object>> isolateHost(@RequestBody Map<String, String> body) {
        String hostId = body.get("hostId");
        if (hostId == null || hostId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", (Object) "Host ID required"));
        }
        return ResponseEntity.ok(simulationService.isolateHost(hostId));
    }

    @PostMapping("/actions/remove-cron")
    public ResponseEntity<Map<String, Object>> removeCron() {
        return ResponseEntity.ok(simulationService.removeCron());
    }

    @GetMapping("/actions/available")
    public Map<String, Object> availableActions() {
        return simulationService.getAvailableActions();
    }
}
