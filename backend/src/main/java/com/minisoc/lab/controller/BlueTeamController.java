package com.minisoc.lab.controller;

import com.minisoc.lab.model.AnalysisReport;
import com.minisoc.lab.model.DetectionSignature;
import com.minisoc.lab.service.MalwareAnalysisService;
import com.minisoc.lab.service.SignatureService;
import com.minisoc.lab.service.SimulationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/blue-team")
public class BlueTeamController {

    private final SimulationService simulationService;
    private final MalwareAnalysisService malwareAnalysisService;
    private final SignatureService signatureService;

    public BlueTeamController(SimulationService simulationService,
                              MalwareAnalysisService malwareAnalysisService,
                              SignatureService signatureService) {
        this.simulationService = simulationService;
        this.malwareAnalysisService = malwareAnalysisService;
        this.signatureService = signatureService;
    }

    @GetMapping("/processes")
    public ResponseEntity<Map<String, Object>> getProcesses() {
        return ResponseEntity.ok(simulationService.getProcessList());
    }

    @GetMapping("/connections")
    public ResponseEntity<Map<String, Object>> getConnections() {
        return ResponseEntity.ok(simulationService.getNetworkConnections());
    }

    @GetMapping("/files")
    public ResponseEntity<Map<String, Object>> getFiles() {
        return ResponseEntity.ok(simulationService.getFileList());
    }

    @PostMapping("/scan-file")
    public ResponseEntity<Map<String, Object>> scanFile(@RequestBody Map<String, String> body) {
        String filePath = body.get("filePath");
        if (filePath == null || filePath.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "reason", "Missing 'filePath'"));
        }
        return ResponseEntity.ok(simulationService.scanFile(filePath));
    }

    @GetMapping("/behavioral-analysis")
    public ResponseEntity<Map<String, Object>> behavioralAnalysis(
            @RequestParam(defaultValue = "5") int minutes) {
        return ResponseEntity.ok(simulationService.behavioralAnalysis(minutes));
    }

    // ==================== NEW: Malware Analysis ====================

    @PostMapping("/analyze-file")
    public ResponseEntity<?> analyzeFile(@RequestBody Map<String, String> body) {
        String filePath = body.get("filePath");
        if (filePath == null || filePath.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "reason", "Missing 'filePath'"));
        }
        AnalysisReport report = malwareAnalysisService.analyze(filePath);
        // Award blue team points for analysis
        simulationService.getGameState().addBlueScore(20);
        simulationService.pushLog("OK", "blue_team: malware analysis completed on " + filePath + " (+20 pts)");
        // Unredact alerts referencing this file
        simulationService.unredactEventsForFile(filePath);
        return ResponseEntity.ok(report);
    }

    // ==================== NEW: Signature Management ====================

    @PostMapping("/signatures")
    public ResponseEntity<?> createSignature(@RequestBody Map<String, String> body) {
        String typeStr = body.get("type");
        String pattern = body.get("pattern");
        String description = body.getOrDefault("description", "");
        if (typeStr == null || pattern == null || pattern.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "reason", "Missing type or pattern"));
        }
        try {
            DetectionSignature.SignatureType type = DetectionSignature.SignatureType.valueOf(typeStr.toUpperCase());
            DetectionSignature sig = signatureService.createSignature(type, pattern, description);
            simulationService.getGameState().addBlueScore(30);
            simulationService.getGameState().touchBlueAction();
            simulationService.pushLog("OK", "blue_team: deployed signature " + sig.getId() + " [" + type + ": " + pattern + "] (+30 pts)");
            return ResponseEntity.ok(sig);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("success", (Object) false, "reason", "Invalid type. Use: HASH, STRING, NETWORK_IOC"));
        }
    }

    @GetMapping("/signatures")
    public ResponseEntity<List<DetectionSignature>> getSignatures() {
        return ResponseEntity.ok(new java.util.ArrayList<>(signatureService.getAll()));
    }

    @DeleteMapping("/signatures/{id}")
    public ResponseEntity<?> deleteSignature(@PathVariable String id) {
        signatureService.deactivate(id);
        return ResponseEntity.ok(Map.of("success", true, "id", id));
    }

    // ==================== NEW: Honeypot ====================

    @PostMapping("/plant-honeypot")
    public ResponseEntity<?> plantHoneypot(@RequestBody Map<String, String> body) {
        String path = body.get("path");
        if (path == null || path.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "reason", "Missing 'path'"));
        }
        return ResponseEntity.ok(simulationService.plantHoneypot(path));
    }

    // ==================== NEW: Analyst Notes ====================

    @PatchMapping("/alerts/{alertId}/notes")
    public ResponseEntity<?> updateAlertNotes(@PathVariable String alertId, @RequestBody Map<String, String> body) {
        String notes = body.get("notes");
        Map<String, Object> result = simulationService.updateAlertNotes(alertId, notes);
        if (result == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(result);
    }
}
