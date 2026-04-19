package com.minisoc.lab.controller;

import com.minisoc.lab.model.DetectionSignature;
import com.minisoc.lab.service.CommandStreamService;
import com.minisoc.lab.service.SignatureService;
import com.minisoc.lab.service.SimulationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/red-team")
public class RedTeamController {

    private final SimulationService simulationService;
    private final CommandStreamService commandStreamService;
    private final SignatureService signatureService;

    public RedTeamController(SimulationService simulationService,
                             CommandStreamService commandStreamService,
                             SignatureService signatureService) {
        this.simulationService = simulationService;
        this.commandStreamService = commandStreamService;
        this.signatureService = signatureService;
    }

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
     * Execute with SSE streaming output. Returns a commandId to subscribe to via /api/stream/command/{id}.
     */
    @PostMapping("/execute-stream")
    public ResponseEntity<?> executeStream(@RequestBody Map<String, String> body) {
        String container = body.get("container");
        String command = body.get("command");

        if (container == null || container.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "reason", "Missing 'container'"));
        }
        if (command == null || command.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "reason", "Missing 'command'"));
        }
        if (!container.equals("soc-victim") && !container.equals("soc-attacker")) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "reason", "Container must be 'soc-victim' or 'soc-attacker'"
            ));
        }

        // Log it in simulation
        simulationService.pushLog("RED", "red_team: streaming exec on " + container + " → " + command);

        String commandId = commandStreamService.startStream(container, command, (exitCode, pts) -> {
            int points = exitCode == 0 ? 15 : 5;
            simulationService.getGameState().addRedScore(points);
            simulationService.pushLog("RED", "red_team: stream command finished (exit=" + exitCode + ", +" + points + " pts)");
        });

        return ResponseEntity.ok(Map.of("commandId", commandId, "container", container));
    }

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

        // Check if Blue team has deployed a hash signature that blocks this payload
        try {
            byte[] decoded = Base64.getDecoder().decode(payload);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(decoded);
            StringBuilder hex = new StringBuilder();
            for (byte b : hashBytes) hex.append(String.format("%02x", b));
            String sha256 = hex.toString();

            DetectionSignature hashMatch = signatureService.matchByHash(sha256);
            if (hashMatch != null) {
                simulationService.getGameState().addRedScore(-15);
                simulationService.pushLog("CRIT", "BLOCKED: red upload '" + name + "' matched hash signature " +
                        hashMatch.getId() + " — payload rejected (-15 pts)");
                return ResponseEntity.status(403).body(Map.of(
                        "success", (Object) false,
                        "blocked", (Object) true,
                        "signatureId", hashMatch.getId(),
                        "reason", "Payload hash matches deployed detection signature"
                ));
            }
        } catch (Exception e) {
            // If decoding fails, proceed without hash check
        }

        String obfuscation = body.getOrDefault("obfuscation", "none");
        String targetPath = body.get("targetPath");

        Map<String, Object> result = simulationService.uploadMalware(name, payload, obfuscation, targetPath);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/history")
    public ResponseEntity<List<Map<String, Object>>> getHistory() {
        return ResponseEntity.ok(simulationService.getRedTeamCommandLog());
    }
}
