package com.minisoc.lab.service;

import com.minisoc.lab.model.DetectionSignature;
import com.minisoc.lab.model.SystemEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages IOC signatures created by the Blue Team.
 *
 * Signature types:
 *  - HASH      : SHA-256 of a file — blocks re-upload and fires on file creation event
 *  - STRING    : substring match in cmdline or file content
 *  - NETWORK_IOC: IP address (exact match in remoteIp or cmdline)
 */
@Service
public class SignatureService {

    private static final Logger log = LoggerFactory.getLogger(SignatureService.class);

    private final ConcurrentHashMap<String, DetectionSignature> signatures = new ConcurrentHashMap<>();

    public DetectionSignature createSignature(DetectionSignature.SignatureType type, String pattern,
                                              String description) {
        // Deduplicate: same type + pattern
        for (DetectionSignature existing : signatures.values()) {
            if (existing.isActive() && existing.getType() == type
                    && existing.getPattern().equalsIgnoreCase(pattern)) {
                return existing; // return existing, don't double-score
            }
        }

        String id = "SIG-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        DetectionSignature sig = new DetectionSignature(id, type, pattern, description);
        signatures.put(id, sig);
        log.info("NEW SIGNATURE [{}] type={} pattern={}", id, type, pattern);
        return sig;
    }

    public DetectionSignature getById(String id) {
        return signatures.get(id);
    }

    public void deactivate(String id) {
        DetectionSignature sig = signatures.get(id);
        if (sig != null) sig.setActive(false);
    }

    public Collection<DetectionSignature> getAll() {
        return signatures.values();
    }

    public void clearAll() {
        signatures.clear();
    }

    /**
     * Check if a file hash matches any active HASH signature.
     * Returns the first matching signature or null.
     */
    public DetectionSignature matchByHash(String sha256) {
        if (sha256 == null || sha256.equals("unknown")) return null;
        for (DetectionSignature sig : signatures.values()) {
            if (sig.isActive() && sig.getType() == DetectionSignature.SignatureType.HASH
                    && sig.getPattern().equalsIgnoreCase(sha256)) {
                sig.incrementMatchCount();
                return sig;
            }
        }
        return null;
    }

    /**
     * Check if a SystemEvent matches any active signature.
     * Returns list of matching signatures (updates matchCount on each).
     */
    public List<DetectionSignature> matchEvent(SystemEvent event) {
        List<DetectionSignature> matches = new ArrayList<>();
        for (DetectionSignature sig : signatures.values()) {
            if (!sig.isActive()) continue;
            if (eventMatches(event, sig)) {
                sig.incrementMatchCount();
                matches.add(sig);
            }
        }
        return matches;
    }

    private boolean eventMatches(SystemEvent event, DetectionSignature sig) {
        return switch (sig.getType()) {
            case HASH -> matchesHash(event, sig.getPattern());
            case STRING -> matchesString(event, sig.getPattern());
            case NETWORK_IOC -> matchesNetworkIOC(event, sig.getPattern());
        };
    }

    private boolean matchesHash(SystemEvent event, String hash) {
        // Hash signatures fire on file creation events — we can't compute hash here
        // but we can check if the file path is in the event and let the analysis service confirm
        return false; // hash matching is done at upload time via matchByHash()
    }

    private boolean matchesString(SystemEvent event, String pattern) {
        String lower = pattern.toLowerCase();
        if (event.getCmdline() != null && event.getCmdline().toLowerCase().contains(lower)) return true;
        if (event.getProcessName() != null && event.getProcessName().toLowerCase().contains(lower)) return true;
        if (event.getCronEntry() != null && event.getCronEntry().toLowerCase().contains(lower)) return true;
        return false;
    }

    private boolean matchesNetworkIOC(SystemEvent event, String ip) {
        if (ip.equals(event.getRemoteIp())) return true;
        if (event.getCmdline() != null && event.getCmdline().contains(ip)) return true;
        return false;
    }
}
