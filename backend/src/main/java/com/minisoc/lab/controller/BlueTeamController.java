package com.minisoc.lab.controller;

import com.minisoc.lab.model.AnalysisReport;
import com.minisoc.lab.model.DetectionSignature;
import com.minisoc.lab.service.MalwareAnalysisService;
import com.minisoc.lab.service.MockAvEngineService;
import com.minisoc.lab.service.NetworkMonitorService;
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
    private final NetworkMonitorService networkMonitorService;
    private final MockAvEngineService mockAvEngineService;

    public BlueTeamController(SimulationService simulationService,
                              MalwareAnalysisService malwareAnalysisService,
                              SignatureService signatureService,
                              NetworkMonitorService networkMonitorService,
                              MockAvEngineService mockAvEngineService) {
        this.simulationService = simulationService;
        this.malwareAnalysisService = malwareAnalysisService;
        this.signatureService = signatureService;
        this.networkMonitorService = networkMonitorService;
        this.mockAvEngineService = mockAvEngineService;
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

    @PostMapping("/plant-http-honeypot")
    public ResponseEntity<?> plantHttpHoneypot(@RequestBody Map<String, String> body) {
        String endpoint = body.getOrDefault("endpoint", "/api/c2/execute");
        return ResponseEntity.ok(simulationService.plantHttpHoneypot(endpoint));
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

    // ==================== Network Monitoring ====================

    @PostMapping("/network/start")
    public ResponseEntity<?> startNetworkMonitoring() {
        networkMonitorService.startMonitoring();
        simulationService.pushLog("OK", "🔵 BLUE TEAM: Network monitoring activated");
        return ResponseEntity.ok(Map.of("success", true, "message", "Network monitoring started"));
    }

    @PostMapping("/network/stop")
    public ResponseEntity<?> stopNetworkMonitoring() {
        networkMonitorService.stopMonitoring();
        return ResponseEntity.ok(Map.of("success", true, "message", "Network monitoring stopped"));
    }

    @GetMapping("/network/events")
    public ResponseEntity<?> getNetworkEvents(@RequestParam(defaultValue = "100") int limit) {
        return ResponseEntity.ok(Map.of(
                "success", true,
                "events", networkMonitorService.getRecentEvents(limit),
                "active", networkMonitorService.isActive()
        ));
    }

    @GetMapping("/network/stats")
    public ResponseEntity<?> getNetworkStats() {
        return ResponseEntity.ok(Map.of(
                "success", true,
                "stats", networkMonitorService.getConnectionStats(),
                "active", networkMonitorService.isActive()
        ));
    }

    @PostMapping("/network/block")
    public ResponseEntity<?> networkBlockIp(@RequestBody Map<String, String> body) {
        String ip = body.get("ip");
        if (ip == null || ip.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "reason", "Missing 'ip'"));
        }
        var result = networkMonitorService.blockIp(ip);
        if (Boolean.TRUE.equals(result.get("success"))) {
            simulationService.getGameState().addBlueScore(25);
            simulationService.getGameState().touchBlueAction();
            simulationService.pushLog("OK", "🔵 BLUE TEAM: Blocked IP " + ip + " via network monitor (+25 pts)");
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping("/network/reject")
    public ResponseEntity<?> networkRejectIp(@RequestBody Map<String, String> body) {
        String ip = body.get("ip");
        if (ip == null || ip.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "reason", "Missing 'ip'"));
        }
        var result = networkMonitorService.rejectIp(ip);
        if (Boolean.TRUE.equals(result.get("success"))) {
            simulationService.getGameState().addBlueScore(20);
            simulationService.getGameState().touchBlueAction();
            simulationService.pushLog("OK", "🔵 BLUE TEAM: Rejected packets from " + ip + " (+20 pts)");
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping("/network/terminate")
    public ResponseEntity<?> networkTerminateConnections(@RequestBody Map<String, String> body) {
        String ip = body.get("ip");
        if (ip == null || ip.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "reason", "Missing 'ip'"));
        }
        var result = networkMonitorService.terminateConnections(ip);
        if (Boolean.TRUE.equals(result.get("success"))) {
            simulationService.getGameState().addBlueScore(15);
            simulationService.getGameState().touchBlueAction();
            simulationService.pushLog("OK", "🔵 BLUE TEAM: Terminated connections from " + ip + " (+15 pts)");
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping("/network/probe")
    public ResponseEntity<?> activeProbe() {
        var result = networkMonitorService.activeProbe();
        simulationService.getGameState().addBlueScore(10);
        simulationService.getGameState().touchBlueAction();
        simulationService.pushLog("OK", "🔵 BLUE TEAM: Active network probe completed (+10 pts)");
        return ResponseEntity.ok(result);
    }

    // ==================== Mock AV Engine Scan ====================

    @PostMapping("/av-scan")
    public ResponseEntity<?> avScanHash(@RequestBody Map<String, String> body) {
        String hash = body.get("hash");
        if (hash == null || hash.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "reason", "Missing 'hash'"));
        }
        var result = mockAvEngineService.scanHash(hash);
        simulationService.getGameState().touchBlueAction();
        return ResponseEntity.ok(result);
    }

    @PostMapping("/av-scan/deploy")
    public ResponseEntity<?> avScanDeploy(@RequestBody Map<String, String> body) {
        String hash = body.get("hash");
        if (hash == null || hash.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "reason", "Missing 'hash'"));
        }
        // Scan first to determine confidence
        var scanResult = mockAvEngineService.scanHash(hash);
        double detectionRate = ((Number) scanResult.get("detectionRate")).doubleValue();
        int points = ((Number) scanResult.get("confidencePoints")).intValue();

        // Deploy the signature
        DetectionSignature sig = signatureService.createSignature(
                DetectionSignature.SignatureType.HASH, hash,
                "AV-validated hash (detection: " + Math.round(detectionRate) + "%)");

        simulationService.getGameState().addBlueScore(points);
        simulationService.getGameState().touchBlueAction();
        simulationService.pushLog("OK", String.format(
                "🔵 BLUE TEAM: Deployed AV-validated signature %s (%.0f%% detection, +%d pts)",
                sig.getId(), detectionRate, points));

        return ResponseEntity.ok(Map.of(
                "success", true,
                "signatureId", sig.getId(),
                "detectionRate", detectionRate,
                "points", points,
                "verdict", scanResult.get("verdict")
        ));
    }
}
