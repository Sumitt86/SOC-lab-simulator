package com.minisoc.lab.controller;

import com.minisoc.lab.service.HoneypotEndpointHandler;
import com.minisoc.lab.service.SimulationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;

import java.util.Map;

/**
 * HTTP honeypot endpoint — fake C2 server.
 * This is deployed on the victim container as a mock backdoor.
 * Every request is logged and triggers an alert.
 */
@RestController
@RequestMapping("/api/c2")
public class HoneypotController {

    @Autowired
    private SimulationService simulationService;

    /**
     * Fake C2 execute endpoint.
     * Red Team can POST a command, receives fake response, triggers honeypot alert.
     */
    @PostMapping("/execute")
    public ResponseEntity<?> executeCommand(@RequestBody Map<String, Object> payload) {
        String command = payload.getOrDefault("cmd", "").toString();
        String sourceIp = getClientIp();

        // Log honeypot hit with full payload
        simulationService.recordHoneypotHit(sourceIp, command, payload);

        // Generate fake C2 response
        Map<String, Object> response = HoneypotEndpointHandler.generateFakeC2Response(command);

        return ResponseEntity.ok(response);
    }

    /**
     * Fake credentials endpoint.
     */
    @PostMapping("/credentials")
    public ResponseEntity<?> getCredentials() {
        String sourceIp = getClientIp();
        simulationService.recordHoneypotHit(sourceIp, "GET_CREDENTIALS", Map.of("endpoint", "/api/c2/credentials"));
        return ResponseEntity.ok(HoneypotEndpointHandler.generateFakeCredentials());
    }

    /**
     * Fake system info endpoint.
     */
    @PostMapping("/sysinfo")
    public ResponseEntity<?> getSystemInfo() {
        String sourceIp = getClientIp();
        simulationService.recordHoneypotHit(sourceIp, "GET_SYSINFO", Map.of("endpoint", "/api/c2/sysinfo"));
        return ResponseEntity.ok(HoneypotEndpointHandler.generateFakeSystemInfo());
    }

    /**
     * Fake status/health check endpoint.
     */
    @GetMapping("/status")
    public ResponseEntity<?> getStatus() {
        return ResponseEntity.ok(Map.of(
            "status", "online",
            "version", "2.1.4",
            "timestamp", System.currentTimeMillis()
        ));
    }

    private String getClientIp() {
        // In containerized environment, use X-Forwarded-For if available, else use context
        // For lab purposes, this is a simplified extraction
        return "attacker_ip";
    }
}
