package com.minisoc.lab.model;

/**
 * A detection rule that maps log patterns to MITRE ATT&CK techniques.
 * Used by DetectionService to generate alerts from victim logs.
 */
public class DetectionRule {

    public enum Severity { LOW, MEDIUM, HIGH, CRITICAL }
    public enum LogSource { APACHE, SSH, AUDIT, CRON, DNS, PROCESS, NETWORK }

    private String ruleId;        // e.g. "DET-001"
    private String name;
    private String mitreId;       // e.g. "T1190"
    private String mitreTactic;   // e.g. "Initial Access"
    private LogSource logSource;
    private String pattern;       // regex to match in log lines
    private Severity severity;
    private boolean enabled;

    public DetectionRule() {}

    public DetectionRule(String ruleId, String name, String mitreId, String mitreTactic,
                         LogSource logSource, String pattern, Severity severity) {
        this.ruleId = ruleId;
        this.name = name;
        this.mitreId = mitreId;
        this.mitreTactic = mitreTactic;
        this.logSource = logSource;
        this.pattern = pattern;
        this.severity = severity;
        this.enabled = true;
    }

    public String getRuleId() { return ruleId; }
    public String getName() { return name; }
    public String getMitreId() { return mitreId; }
    public String getMitreTactic() { return mitreTactic; }
    public LogSource getLogSource() { return logSource; }
    public String getPattern() { return pattern; }
    public Severity getSeverity() { return severity; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
