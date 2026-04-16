package com.minisoc.lab.controller;

import com.minisoc.lab.model.AlertItem;
import com.minisoc.lab.model.DashboardSummary;
import com.minisoc.lab.model.LogEntry;
import com.minisoc.lab.model.TodoItem;
import com.minisoc.lab.service.SimulationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class SimulationController {

    private final SimulationService simulationService;

    public SimulationController(SimulationService simulationService) {
        this.simulationService = simulationService;
    }

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

    @PostMapping("/simulation/reset")
    public ResponseEntity<Map<String, String>> reset() {
        simulationService.resetSimulation();
        return ResponseEntity.ok(Map.of("message", "Simulation reset"));
    }
}
