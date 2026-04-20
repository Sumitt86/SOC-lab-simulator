package com.minisoc.lab.model;

import java.util.List;
import java.util.Map;

/**
 * Defines a single attack vector with its kill chain stages,
 * MITRE ATT&CK technique mappings, and scoring.
 */
public class AttackVector {

    private String id;               // e.g. "web-sqli", "ssh-bruteforce"
    private String name;             // e.g. "Web Application Exploitation"
    private String description;
    private List<KillChainStage> killChain;

    public AttackVector() {}

    public AttackVector(String id, String name, String description, List<KillChainStage> killChain) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.killChain = killChain;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public List<KillChainStage> getKillChain() { return killChain; }

    /**
     * A single stage in the kill chain for this vector.
     */
    public static class KillChainStage {
        private int order;
        private String stageId;          // e.g. "recon", "exploit", "persist"
        private String name;
        private String mitreId;          // e.g. "T1190"
        private String mitreName;        // e.g. "Exploit Public-Facing Application"
        private String mitreTactic;      // e.g. "Initial Access"
        private String command;          // command template to execute
        private String targetContainer;  // "soc-attacker" or "soc-victim"
        private int points;
        private List<String> prerequisites; // stageIds that must complete first
        private Map<String, String> detectionHints; // hints for blue team

        public KillChainStage() {}

        public KillChainStage(int order, String stageId, String name, String mitreId,
                              String mitreName, String mitreTactic, String command,
                              String targetContainer, int points,
                              List<String> prerequisites, Map<String, String> detectionHints) {
            this.order = order;
            this.stageId = stageId;
            this.name = name;
            this.mitreId = mitreId;
            this.mitreName = mitreName;
            this.mitreTactic = mitreTactic;
            this.command = command;
            this.targetContainer = targetContainer;
            this.points = points;
            this.prerequisites = prerequisites;
            this.detectionHints = detectionHints;
        }

        public int getOrder() { return order; }
        public String getStageId() { return stageId; }
        public String getName() { return name; }
        public String getMitreId() { return mitreId; }
        public String getMitreName() { return mitreName; }
        public String getMitreTactic() { return mitreTactic; }
        public String getCommand() { return command; }
        public String getTargetContainer() { return targetContainer; }
        public int getPoints() { return points; }
        public List<String> getPrerequisites() { return prerequisites; }
        public Map<String, String> getDetectionHints() { return detectionHints; }
    }
}
