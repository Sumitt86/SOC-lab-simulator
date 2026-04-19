package com.minisoc.lab.model;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

public class DetectionSignature {

    public enum SignatureType {
        HASH,           // SHA-256 exact match
        STRING,         // substring in file content or cmdline
        NETWORK_IOC     // IP address or hostname
    }

    private final String id;
    private final SignatureType type;
    private final String pattern;
    private final String description;
    private final Instant createdAt;
    private final AtomicInteger matchCount = new AtomicInteger(0);
    private volatile boolean active;

    public DetectionSignature(String id, SignatureType type, String pattern, String description) {
        this.id = id;
        this.type = type;
        this.pattern = pattern;
        this.description = description != null ? description : "";
        this.createdAt = Instant.now();
        this.active = true;
    }

    public void incrementMatchCount() {
        matchCount.incrementAndGet();
    }

    public String getId() { return id; }
    public SignatureType getType() { return type; }
    public String getPattern() { return pattern; }
    public String getDescription() { return description; }
    public Instant getCreatedAt() { return createdAt; }
    public int getMatchCount() { return matchCount.get(); }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
