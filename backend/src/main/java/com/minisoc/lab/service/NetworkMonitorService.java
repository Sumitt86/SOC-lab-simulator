package com.minisoc.lab.service;

import com.minisoc.lab.executor.SystemCommandExecutor;
import com.minisoc.lab.model.SystemEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Passive network traffic monitor for container network.
 * Captures and analyzes traffic via tcpdump on the victim container.
 * Detects port scans, brute-force attempts, and classifies attacks.
 */
@Service
public class NetworkMonitorService {

    private static final Logger log = LoggerFactory.getLogger(NetworkMonitorService.class);

    private final SystemCommandExecutor commandExecutor;
    private final StreamingService streamingService;

    @Value("${cyber-range.victim.container:soc-victim}")
    private String victimContainer;

    // Tracked connections for anomaly detection
    private final ConcurrentHashMap<String, ConnectionTracker> trackers = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<NetworkEvent> recentEvents = new CopyOnWriteArrayList<>();
    private final AtomicLong eventIdCounter = new AtomicLong(1);
    private volatile boolean monitoringActive = false;

    // Port scan detection thresholds
    private static final int PORT_SCAN_THRESHOLD = 5;      // N unique ports from same IP in window
    private static final long PORT_SCAN_WINDOW_MS = 10_000; // 10 seconds
    private static final int BRUTE_FORCE_THRESHOLD = 5;     // N connections to same port from same IP
    private static final long BRUTE_FORCE_WINDOW_MS = 30_000;

    private static final Pattern SS_LINE = Pattern.compile(
            "(tcp|udp)\\s+(LISTEN|ESTAB|SYN-SENT|SYN-RECV|FIN-WAIT|TIME-WAIT|CLOSE-WAIT|UNCONN)\\s+" +
            "(\\d+)\\s+(\\d+)\\s+" +
            "([\\d.*:]+):(\\d+|\\*)\\s+" +
            "([\\d.*:]+):(\\d+|\\*)\\s*" +
            "(?:users:\\(\\(\"([^\"]+)\".*pid=(\\d+))?"
    );

    public NetworkMonitorService(SystemCommandExecutor commandExecutor, StreamingService streamingService) {
        this.commandExecutor = commandExecutor;
        this.streamingService = streamingService;
    }

    public void startMonitoring() {
        monitoringActive = true;
        trackers.clear();
        recentEvents.clear();
    }

    public void stopMonitoring() {
        monitoringActive = false;
    }

    public boolean isActive() {
        return monitoringActive;
    }

    /**
     * Polls network state every 2 seconds when monitoring is active.
     */
    @Scheduled(fixedRate = 2000)
    public void pollNetworkState() {
        if (!monitoringActive || victimContainer == null || victimContainer.isBlank()) return;

        try {
            // Get current connections via ss
            var result = commandExecutor.execute(victimContainer,
                    "ss -tulnp 2>/dev/null | tail -n +2");
            if (result.success() && result.stdout() != null && !result.stdout().isBlank()) {
                parseAndProcessConnections(result.stdout());
            }

            // Also get recent SYN packets (port scan indicator)
            var tcpdumpResult = commandExecutor.execute(victimContainer,
                    "timeout 1 tcpdump -c 50 -nn -q 'tcp[tcpflags] & tcp-syn != 0' 2>/dev/null || true");
            if (tcpdumpResult.success() && tcpdumpResult.stdout() != null && !tcpdumpResult.stdout().isBlank()) {
                parseTcpdumpOutput(tcpdumpResult.stdout());
            }

            // Trim old events (keep last 500)
            while (recentEvents.size() > 500) {
                recentEvents.remove(0);
            }

            // Run anomaly detection
            detectAnomalies();

        } catch (Exception e) {
            log.debug("Network poll error: {}", e.getMessage());
        }
    }

    private void parseAndProcessConnections(String ssOutput) {
        for (String line : ssOutput.split("\n")) {
            line = line.trim();
            if (line.isEmpty()) continue;

            Matcher m = SS_LINE.matcher(line);
            if (!m.find()) continue;

            String protocol = m.group(1);
            String state = m.group(2);
            String localAddr = m.group(5);
            String localPortStr = m.group(6);
            String remoteAddr = m.group(7);
            String remotePortStr = m.group(8);
            String processName = m.groupCount() >= 8 ? m.group(8) : null;
            String pidStr = m.groupCount() >= 9 ? m.group(9) : null;

            if ("*".equals(localPortStr) || "*".equals(remotePortStr)) continue;
            if ("0.0.0.0".equals(remoteAddr) || "::".equals(remoteAddr) || "*".equals(remoteAddr)) continue;

            int localPort = parsePort(localPortStr);
            int remotePort = parsePort(remotePortStr);
            if (localPort < 0 || remotePort < 0) continue;

            NetworkEvent evt = new NetworkEvent();
            evt.id = eventIdCounter.getAndIncrement();
            evt.timestamp = Instant.now();
            evt.protocol = protocol.toUpperCase();
            evt.state = state;
            evt.sourceIp = remoteAddr;
            evt.sourcePort = remotePort;
            evt.destIp = localAddr;
            evt.destPort = localPort;
            evt.processName = processName;
            evt.pid = pidStr != null ? parseLong(pidStr) : null;
            evt.classification = "UNKNOWN";
            evt.raw = line;

            // Track this connection
            String trackerKey = remoteAddr;
            trackers.computeIfAbsent(trackerKey, k -> new ConnectionTracker(k)).recordConnection(localPort, evt.timestamp);

            recentEvents.add(evt);
        }
    }

    private void parseTcpdumpOutput(String output) {
        // Parse tcpdump -nn -q output: "timestamp IP src.port > dst.port: tcp flags"
        Pattern tcpdumpPattern = Pattern.compile(
                "(\\d+\\.\\d+\\.\\d+\\.\\d+)\\.(\\d+)\\s*>\\s*(\\d+\\.\\d+\\.\\d+\\.\\d+)\\.(\\d+).*?(S|Flags \\[S\\])"
        );

        for (String line : output.split("\n")) {
            Matcher m = tcpdumpPattern.matcher(line);
            if (!m.find()) continue;

            String srcIp = m.group(1);
            int srcPort = parsePort(m.group(2));
            String dstIp = m.group(3);
            int dstPort = parsePort(m.group(4));
            if (srcPort < 0 || dstPort < 0) continue;

            NetworkEvent evt = new NetworkEvent();
            evt.id = eventIdCounter.getAndIncrement();
            evt.timestamp = Instant.now();
            evt.protocol = "TCP";
            evt.state = "SYN";
            evt.sourceIp = srcIp;
            evt.sourcePort = srcPort;
            evt.destIp = dstIp;
            evt.destPort = dstPort;
            evt.flags = "SYN";
            evt.classification = "UNKNOWN";
            evt.raw = line.trim();

            String trackerKey = srcIp;
            trackers.computeIfAbsent(trackerKey, k -> new ConnectionTracker(k)).recordConnection(dstPort, evt.timestamp);

            recentEvents.add(evt);
        }
    }

    private void detectAnomalies() {
        Instant now = Instant.now();

        for (Map.Entry<String, ConnectionTracker> entry : trackers.entrySet()) {
            ConnectionTracker tracker = entry.getValue();
            String ip = entry.getKey();

            // Port scan detection
            int uniquePorts = tracker.uniquePortsInWindow(now, PORT_SCAN_WINDOW_MS);
            if (uniquePorts >= PORT_SCAN_THRESHOLD && !tracker.portScanAlerted) {
                tracker.portScanAlerted = true;
                NetworkAlert alert = new NetworkAlert();
                alert.id = "NET-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
                alert.timestamp = now;
                alert.sourceIp = ip;
                alert.classification = "PORT_SCAN";
                alert.severity = "CRITICAL";
                alert.title = "Port Scan Detected";
                alert.detail = String.format("IP %s probed %d unique ports in %ds — likely nmap scan",
                        ip, uniquePorts, PORT_SCAN_WINDOW_MS / 1000);
                alert.portsScanned = tracker.getRecentPorts(now, PORT_SCAN_WINDOW_MS);
                alert.packetCount = tracker.totalConnections(now, PORT_SCAN_WINDOW_MS);
                alert.mitreId = "T1046";
                alert.mitreName = "Network Service Discovery";
                alert.actionable = true;

                streamingService.pushGameEvent("network-alert", alertToMap(alert));
                log.info("PORT SCAN detected from {}: {} ports", ip, uniquePorts);
            }

            // Brute force detection
            Map<Integer, Integer> portHits = tracker.connectionsPerPortInWindow(now, BRUTE_FORCE_WINDOW_MS);
            for (Map.Entry<Integer, Integer> portEntry : portHits.entrySet()) {
                int port = portEntry.getKey();
                int hits = portEntry.getValue();
                String bfKey = ip + ":" + port;
                if (hits >= BRUTE_FORCE_THRESHOLD && !tracker.bruteForceAlerted.contains(bfKey)) {
                    tracker.bruteForceAlerted.add(bfKey);
                    NetworkAlert alert = new NetworkAlert();
                    alert.id = "NET-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
                    alert.timestamp = now;
                    alert.sourceIp = ip;
                    alert.classification = "BRUTE_FORCE";
                    alert.severity = "HIGH";
                    alert.title = "Brute Force Attempt";
                    alert.detail = String.format("IP %s made %d connections to port %d in %ds",
                            ip, hits, port, BRUTE_FORCE_WINDOW_MS / 1000);
                    alert.targetPort = port;
                    alert.packetCount = hits;
                    alert.mitreId = "T1110";
                    alert.mitreName = "Brute Force";
                    alert.actionable = true;

                    streamingService.pushGameEvent("network-alert", alertToMap(alert));
                    log.info("BRUTE FORCE detected from {} to port {}: {} hits", ip, port, hits);
                }
            }
        }
    }

    /**
     * Get recent network events for the frontend.
     */
    public List<Map<String, Object>> getRecentEvents(int limit) {
        List<Map<String, Object>> result = new ArrayList<>();
        int start = Math.max(0, recentEvents.size() - limit);
        for (int i = recentEvents.size() - 1; i >= start; i--) {
            result.add(eventToMap(recentEvents.get(i)));
        }
        return result;
    }

    /**
     * Get active connection trackers (per-IP stats).
     */
    public List<Map<String, Object>> getConnectionStats() {
        List<Map<String, Object>> result = new ArrayList<>();
        Instant now = Instant.now();
        for (Map.Entry<String, ConnectionTracker> entry : trackers.entrySet()) {
            ConnectionTracker t = entry.getValue();
            Map<String, Object> stat = new LinkedHashMap<>();
            stat.put("ip", entry.getKey());
            stat.put("totalConnections", t.totalConnections(now, 60_000));
            stat.put("uniquePorts", t.uniquePortsInWindow(now, 60_000));
            stat.put("portScanDetected", t.portScanAlerted);
            stat.put("bruteForceDetected", !t.bruteForceAlerted.isEmpty());
            stat.put("recentPorts", t.getRecentPorts(now, 60_000));
            result.add(stat);
        }
        result.sort((a, b) -> Integer.compare((int) b.get("totalConnections"), (int) a.get("totalConnections")));
        return result;
    }

    /**
     * Block an IP via iptables on the victim container.
     */
    public Map<String, Object> blockIp(String ip) {
        if (victimContainer == null || victimContainer.isBlank()) {
            return Map.of("success", false, "reason", "No victim container");
        }
        String cmd = String.format(
                "iptables -A INPUT -s %s -j DROP && iptables -A OUTPUT -d %s -j DROP",
                ip, ip);
        var result = commandExecutor.execute(victimContainer, cmd);
        return Map.of("success", result.success(), "ip", ip,
                "details", result.success() ? "IP blocked" : result.stderr());
    }

    /**
     * Drop/reject packets from an IP.
     */
    public Map<String, Object> rejectIp(String ip) {
        if (victimContainer == null || victimContainer.isBlank()) {
            return Map.of("success", false, "reason", "No victim container");
        }
        String cmd = String.format(
                "iptables -A INPUT -s %s -j REJECT && iptables -A OUTPUT -d %s -j REJECT",
                ip, ip);
        var result = commandExecutor.execute(victimContainer, cmd);
        return Map.of("success", result.success(), "ip", ip,
                "details", result.success() ? "IP rejected" : result.stderr());
    }

    /**
     * Kill all connections from an IP by killing associated processes.
     */
    public Map<String, Object> terminateConnections(String ip) {
        if (victimContainer == null || victimContainer.isBlank()) {
            return Map.of("success", false, "reason", "No victim container");
        }
        String cmd = String.format("pkill -f '%s' 2>/dev/null; ss -K dst %s 2>/dev/null; echo done", ip, ip);
        var result = commandExecutor.execute(victimContainer, cmd);
        return Map.of("success", result.success(), "ip", ip,
                "details", "Connections terminated");
    }

    /**
     * Run active network probe (Blue Team initiated).
     */
    public Map<String, Object> activeProbe() {
        if (victimContainer == null || victimContainer.isBlank()) {
            return Map.of("success", false, "reason", "No victim container");
        }

        List<Map<String, Object>> findings = new ArrayList<>();

        // Check for listening ports
        var listenResult = commandExecutor.execute(victimContainer,
                "ss -tulnp 2>/dev/null | grep LISTEN");
        if (listenResult.success() && listenResult.stdout() != null) {
            for (String line : listenResult.stdout().split("\n")) {
                if (!line.isBlank()) {
                    findings.add(Map.of("type", "LISTENING_PORT", "detail", line.trim()));
                }
            }
        }

        // Check for established outbound connections
        var outboundResult = commandExecutor.execute(victimContainer,
                "ss -tnp state established 2>/dev/null");
        if (outboundResult.success() && outboundResult.stdout() != null) {
            for (String line : outboundResult.stdout().split("\n")) {
                if (!line.isBlank() && !line.startsWith("State")) {
                    findings.add(Map.of("type", "OUTBOUND_CONNECTION", "detail", line.trim()));
                }
            }
        }

        // Check iptables rules
        var iptResult = commandExecutor.execute(victimContainer,
                "iptables -L -n --line-numbers 2>/dev/null");
        if (iptResult.success() && iptResult.stdout() != null) {
            findings.add(Map.of("type", "FIREWALL_RULES", "detail", iptResult.stdout().trim()));
        }

        // Check for suspicious open files
        var lsofResult = commandExecutor.execute(victimContainer,
                "lsof -i -P -n 2>/dev/null | grep -E 'LISTEN|ESTABLISHED' | head -20");
        if (lsofResult.success() && lsofResult.stdout() != null) {
            for (String line : lsofResult.stdout().split("\n")) {
                if (!line.isBlank()) {
                    findings.add(Map.of("type", "OPEN_SOCKET", "detail", line.trim()));
                }
            }
        }

        return Map.of("success", true, "findings", findings, "count", findings.size(),
                "timestamp", Instant.now().toString());
    }

    // ===== Helper classes =====

    private int parsePort(String s) {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return -1; }
    }

    private Long parseLong(String s) {
        try { return Long.parseLong(s); } catch (NumberFormatException e) { return null; }
    }

    private Map<String, Object> eventToMap(NetworkEvent evt) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", evt.id);
        m.put("timestamp", evt.timestamp.toString());
        m.put("protocol", evt.protocol);
        m.put("state", evt.state);
        m.put("sourceIp", evt.sourceIp);
        m.put("sourcePort", evt.sourcePort);
        m.put("destIp", evt.destIp);
        m.put("destPort", evt.destPort);
        m.put("processName", evt.processName);
        m.put("pid", evt.pid);
        m.put("flags", evt.flags);
        m.put("classification", evt.classification);
        return m;
    }

    private Map<String, Object> alertToMap(NetworkAlert alert) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", alert.id);
        m.put("timestamp", alert.timestamp.toString());
        m.put("sourceIp", alert.sourceIp);
        m.put("classification", alert.classification);
        m.put("severity", alert.severity);
        m.put("title", alert.title);
        m.put("detail", alert.detail);
        m.put("packetCount", alert.packetCount);
        m.put("targetPort", alert.targetPort);
        m.put("portsScanned", alert.portsScanned);
        m.put("mitreId", alert.mitreId);
        m.put("mitreName", alert.mitreName);
        m.put("actionable", alert.actionable);
        return m;
    }

    // ===== Inner data classes =====

    static class NetworkEvent {
        long id;
        Instant timestamp;
        String protocol;
        String state;
        String sourceIp;
        int sourcePort;
        String destIp;
        int destPort;
        String processName;
        Long pid;
        String flags;
        String classification;
        String raw;
    }

    static class NetworkAlert {
        String id;
        Instant timestamp;
        String sourceIp;
        String classification; // PORT_SCAN, BRUTE_FORCE, UNUSUAL_TRAFFIC
        String severity;
        String title;
        String detail;
        int packetCount;
        int targetPort;
        List<Integer> portsScanned;
        String mitreId;
        String mitreName;
        boolean actionable;
    }

    static class ConnectionTracker {
        final String ip;
        final List<ConnectionRecord> records = Collections.synchronizedList(new ArrayList<>());
        volatile boolean portScanAlerted = false;
        final Set<String> bruteForceAlerted = Collections.synchronizedSet(new HashSet<>());

        ConnectionTracker(String ip) { this.ip = ip; }

        void recordConnection(int port, Instant timestamp) {
            records.add(new ConnectionRecord(port, timestamp));
            // Trim old records (> 2 minutes)
            Instant cutoff = Instant.now().minusSeconds(120);
            records.removeIf(r -> r.timestamp.isBefore(cutoff));
        }

        int uniquePortsInWindow(Instant now, long windowMs) {
            Instant cutoff = now.minusMillis(windowMs);
            Set<Integer> ports = new HashSet<>();
            for (ConnectionRecord r : records) {
                if (r.timestamp.isAfter(cutoff)) ports.add(r.port);
            }
            return ports.size();
        }

        List<Integer> getRecentPorts(Instant now, long windowMs) {
            Instant cutoff = now.minusMillis(windowMs);
            Set<Integer> ports = new LinkedHashSet<>();
            for (ConnectionRecord r : records) {
                if (r.timestamp.isAfter(cutoff)) ports.add(r.port);
            }
            return new ArrayList<>(ports);
        }

        int totalConnections(Instant now, long windowMs) {
            Instant cutoff = now.minusMillis(windowMs);
            int count = 0;
            for (ConnectionRecord r : records) {
                if (r.timestamp.isAfter(cutoff)) count++;
            }
            return count;
        }

        Map<Integer, Integer> connectionsPerPortInWindow(Instant now, long windowMs) {
            Instant cutoff = now.minusMillis(windowMs);
            Map<Integer, Integer> portCounts = new HashMap<>();
            for (ConnectionRecord r : records) {
                if (r.timestamp.isAfter(cutoff)) {
                    portCounts.merge(r.port, 1, Integer::sum);
                }
            }
            return portCounts;
        }
    }

    record ConnectionRecord(int port, Instant timestamp) {}
}
