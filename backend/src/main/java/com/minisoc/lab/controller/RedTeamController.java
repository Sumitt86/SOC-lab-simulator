package com.minisoc.lab.controller;

import com.minisoc.lab.service.SimulationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Red Team API — manual offensive operations.
 *
 * Allows the Red Team operator to:
 *  - Execute commands on attacker/victim containers
 *  - Upload malware payloads to the victim
 *  - View command history and audit trail
 */
@RestController
@RequestMapping("/api/red-team")
public class RedTeamController {

    private final SimulationService simulationService;

    public RedTeamController(SimulationService simulationService) {
        this.simulationService = simulationService;
    }

    /**
     * Execute a command on a target container.
     * Body: { "container": "soc-victim"|"soc-attacker", "command": "..." }
     */
    @PostMapping("/execute")
    public ResponseEntity<Map<String, Object>> executeCommand(@RequestBody Map<String, String> body) {
        String container = body.get("container");
        String command = body.get("command");

        if (container == null || container.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "reason", "Missing 'container'"));
        }
        if (command == null || command.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "reason", "Missing 'command'"));
        }

        // Only allow execution on known containers
        if (!container.equals("soc-victim") && !container.equals("soc-attacker")) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "reason", "Container must be 'soc-victim' or 'soc-attacker'"
            ));
        }

        Map<String, Object> result = simulationService.executeRedTeamCommand(container, command);
        return ResponseEntity.ok(result);
    }

    /**
     * Upload malware to the victim container.
     * Body: { "name": "payload.sh", "payload": "<base64>", "obfuscation": "base64"|"hex"|"none", "targetPath": "/tmp/..." }
     */
    @PostMapping("/upload-malware")
    public ResponseEntity<Map<String, Object>> uploadMalware(@RequestBody Map<String, String> body) {
        String name = body.get("name");
        String payload = body.get("payload");

        if (name == null || name.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "reason", "Missing 'name'"));
        }
        if (payload == null || payload.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "reason", "Missing 'payload'"));
        }

        String obfuscation = body.getOrDefault("obfuscation", "none");
        String targetPath = body.get("targetPath");

        Map<String, Object> result = simulationService.uploadMalware(name, payload, obfuscation, targetPath);
        return ResponseEntity.ok(result);
    }

    /**
     * Get Red Team command audit trail.
     */
    @GetMapping("/history")
    public ResponseEntity<List<Map<String, Object>>> getHistory() {
        return ResponseEntity.ok(simulationService.getRedTeamCommandLog());
    }
}
