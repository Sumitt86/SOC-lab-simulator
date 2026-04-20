package com.minisoc.lab.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents a deployed HTTP honeypot endpoint.
 * Acts as a fake C2 server that logs attacker interactions.
 */
public class HttpHoneypot {

    private String id;
    private String path;              // e.g., "/api/c2/execute"
    private String baseUrl;           // Full endpoint: http://victim:port/api/c2/execute
    private Instant deployedAt;
    private boolean active;
    private int hitCount;             // Number of POST requests received

    public HttpHoneypot(String path, String baseUrl) {
        this.id = "HONEYPOT-HTTP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        this.path = path;
        this.baseUrl = baseUrl;
        this.deployedAt = Instant.now();
        this.active = true;
        this.hitCount = 0;
    }

    public void recordHit() {
        this.hitCount++;
    }

    // Getters & Setters
    public String getId() { return id; }
    public String getPath() { return path; }
    public String getBaseUrl() { return baseUrl; }
    public Instant getDeployedAt() { return deployedAt; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public int getHitCount() { return hitCount; }
    public void setHitCount(int count) { this.hitCount = count; }
}
