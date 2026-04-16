package com.minisoc.lab.model;

import java.time.Instant;
import java.util.Map;

/**
 * Raw system event sent by the Python monitoring agent on the victim VM.
 * This is the data-plane input — everything the agent observes about
 * processes, network connections, files, and cron jobs.
 */
public class SystemEvent {

    public enum EventType {
        PROCESS_SPAWNED,
        PROCESS_TERMINATED,
        NETWORK_CONNECTION,
        FILE_CREATED,
        FILE_MODIFIED,
        CRON_ADDED,
        CRON_REMOVED,
        ANOMALY_DETECTED
    }

    private String id;
    private EventType type;
    private String host;
    private Instant timestamp;

    // Process fields
    private Long pid;
    private String processName;
    private Long ppid;
    private String user;
    private String cmdline;
    private String processState;

    // Network fields
    private String localIp;
    private Integer localPort;
    private String remoteIp;
    private Integer remotePort;
    private String connectionState;
    private String protocol;

    // File fields
    private String filePath;
    private String fileAction;

    // Cron fields
    private String cronEntry;

    // Anomaly / confidence
    private Double confidence;
    private String anomalyReason;
    private Integer riskScore;

    // Freeform details
    private Map<String, Object> details;

    public SystemEvent() {
        this.timestamp = Instant.now();
    }

    // ===== Getters & Setters =====

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public EventType getType() { return type; }
    public void setType(EventType type) { this.type = type; }

    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }

    public Long getPid() { return pid; }
    public void setPid(Long pid) { this.pid = pid; }

    public String getProcessName() { return processName; }
    public void setProcessName(String processName) { this.processName = processName; }

    public Long getPpid() { return ppid; }
    public void setPpid(Long ppid) { this.ppid = ppid; }

    public String getUser() { return user; }
    public void setUser(String user) { this.user = user; }

    public String getCmdline() { return cmdline; }
    public void setCmdline(String cmdline) { this.cmdline = cmdline; }

    public String getProcessState() { return processState; }
    public void setProcessState(String processState) { this.processState = processState; }

    public String getLocalIp() { return localIp; }
    public void setLocalIp(String localIp) { this.localIp = localIp; }

    public Integer getLocalPort() { return localPort; }
    public void setLocalPort(Integer localPort) { this.localPort = localPort; }

    public String getRemoteIp() { return remoteIp; }
    public void setRemoteIp(String remoteIp) { this.remoteIp = remoteIp; }

    public Integer getRemotePort() { return remotePort; }
    public void setRemotePort(Integer remotePort) { this.remotePort = remotePort; }

    public String getConnectionState() { return connectionState; }
    public void setConnectionState(String connectionState) { this.connectionState = connectionState; }

    public String getProtocol() { return protocol; }
    public void setProtocol(String protocol) { this.protocol = protocol; }

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    public String getFileAction() { return fileAction; }
    public void setFileAction(String fileAction) { this.fileAction = fileAction; }

    public String getCronEntry() { return cronEntry; }
    public void setCronEntry(String cronEntry) { this.cronEntry = cronEntry; }

    public Double getConfidence() { return confidence; }
    public void setConfidence(Double confidence) { this.confidence = confidence; }

    public String getAnomalyReason() { return anomalyReason; }
    public void setAnomalyReason(String anomalyReason) { this.anomalyReason = anomalyReason; }

    public Integer getRiskScore() { return riskScore; }
    public void setRiskScore(Integer riskScore) { this.riskScore = riskScore; }

    public Map<String, Object> getDetails() { return details; }
    public void setDetails(Map<String, Object> details) { this.details = details; }
}
