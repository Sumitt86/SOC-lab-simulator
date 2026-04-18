package com.minisoc.lab.controller;

import com.minisoc.lab.service.SimulationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Blue Team API — investigation and analysis endpoints.
 *
 * Allows the Blue Team operator to:
 *  - View live processes, network connections, files on the victim
 *  - Scan suspicious files (signature-based detection)
 *  - Run behavioral analysis on recent events
 */
@RestController
@RequestMapping("/api/blue-team")
public class BlueTeamController {

    private final SimulationService simulationService;

    public BlueTeamController(SimulationService simulationService) {
        this.simulationService = simulationService;
    }

    /**
     * Get live process list from victim container.
     */
    @GetMapping("/processes")
    public ResponseEntity<Map<String, Object>> getProcesses() {
        return ResponseEntity.ok(simulationService.getProcessList());
    }

    /**
     * Get live network connections from victim container.
     */
    @GetMapping("/connections")
    public ResponseEntity<Map<String, Object>> getConnections() {
        return ResponseEntity.ok(simulationService.getNetworkConnections());
    }

    /**
     * Get files in monitored directories on victim.
     */
    @GetMapping("/files")
    public ResponseEntity<Map<String, Object>> getFiles() {
        return ResponseEntity.ok(simulationService.getFileList());
    }

    /**
     * Scan a file on the victim for malware signatures.
     * Body: { "filePath": "/tmp/suspicious_file" }
     */
    @PostMapping("/scan-file")
    public ResponseEntity<Map<String, Object>> scanFile(@RequestBody Map<String, String> body) {
        String filePath = body.get("filePath");
        if (filePath == null || filePath.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "reason", "Missing 'filePath'"));
        }
        return ResponseEntity.ok(simulationService.scanFile(filePath));
    }

    /**
     * Run behavioral analysis over recent events.
     * Query param: minutes (default 5)
     */
    @GetMapping("/behavioral-analysis")
    public ResponseEntity<Map<String, Object>> behavioralAnalysis(
            @RequestParam(defaultValue = "5") int minutes) {
        return ResponseEntity.ok(simulationService.behavioralAnalysis(minutes));
    }
}
