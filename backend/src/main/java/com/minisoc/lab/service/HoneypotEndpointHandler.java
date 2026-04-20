package com.minisoc.lab.service;

import java.util.*;

/**
 * Generates deterministic fake C2 command responses.
 * All responses are JSON-formatted and plausible but entirely fabricated.
 */
public class HoneypotEndpointHandler {

    // Fake but plausible credentials pool
    private static final String[][] FAKE_CREDENTIALS = {
        {"root", "K9xL2mN@Pw0"},
        {"admin", "SecureP@ss2024"},
        {"deploy", "D3ployKey123!"},
        {"backup", "BackupRot@123"},
        {"service", "Svc#Acct99"},
        {"postgres", "PgSQL_P@ss99"},
        {"mysql", "MySQL#Secure2"},
        {"monitor", "M0n1t0r$Auth"}
    };

    // Fake process names/PIDs
    private static final String[] FAKE_PROCESSES = {
        "sshd", "systemd", "dbus-daemon", "rsyslog", "cron", "nginx", "postgres",
        "mysqld", "docker", "containerd", "kubelet", "prometheus", "grafana"
    };

    // Fake hostnames
    private static final String[] FAKE_HOSTNAMES = {
        "server01", "prod-api-01", "db-primary", "cache-01", "app-backend",
        "monitoring-hub", "log-aggregator", "load-balancer"
    };

    /**
     * Generate a fake response for a given command (deterministic based on command hash).
     * Response is JSON formatted.
     */
    public static Map<String, Object> generateFakeC2Response(String command) {
        if (command == null || command.isEmpty()) {
            return Map.of(
                "status", "error",
                "message", "No command provided",
                "code", 400
            );
        }

        String cmdLower = command.toLowerCase().trim();
        int seed = command.hashCode(); // Deterministic based on command text
        Random rand = new Random(seed);

        // Command-specific responses
        if (cmdLower.contains("whoami")) {
            return Map.of(
                "status", "success",
                "result", FAKE_CREDENTIALS[rand.nextInt(FAKE_CREDENTIALS.length)][0],
                "uid", 0,
                "gid", 0
            );
        }

        if (cmdLower.contains("id")) {
            String user = FAKE_CREDENTIALS[rand.nextInt(FAKE_CREDENTIALS.length)][0];
            return Map.of(
                "status", "success",
                "result", "uid=0(" + user + ") gid=0(root) groups=0(root)",
                "uid", 0,
                "gid", 0
            );
        }

        if (cmdLower.contains("hostname")) {
            return Map.of(
                "status", "success",
                "result", FAKE_HOSTNAMES[rand.nextInt(FAKE_HOSTNAMES.length)],
                "hostname", FAKE_HOSTNAMES[rand.nextInt(FAKE_HOSTNAMES.length)]
            );
        }

        if (cmdLower.contains("uname")) {
            return Map.of(
                "status", "success",
                "kernel", "Linux",
                "version", "5.15.0-" + (86 + rand.nextInt(20)),
                "machine", "x86_64",
                "result", "Linux prod-server 5." + (15 + rand.nextInt(5)) + ".0-" + (86 + rand.nextInt(20)) + " #1 SMP x86_64 GNU/Linux"
            );
        }

        if (cmdLower.contains("pwd")) {
            return Map.of(
                "status", "success",
                "result", "/home/deploy",
                "cwd", "/home/deploy"
            );
        }

        if (cmdLower.contains("ls") || cmdLower.contains("find")) {
            List<String> fakeFiles = Arrays.asList(
                ".bashrc", ".ssh/id_rsa", ".ssh/authorized_keys", ".profile",
                "config.yml", "secrets.env", "deployment.log", ".aws/credentials"
            );
            return Map.of(
                "status", "success",
                "files", fakeFiles,
                "count", fakeFiles.size(),
                "result", String.join("\n", fakeFiles)
            );
        }

        if (cmdLower.contains("ps") || cmdLower.contains("process")) {
            List<Map<String, Object>> processes = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                processes.add(Map.of(
                    "pid", 1000 + rand.nextInt(9000),
                    "cmd", FAKE_PROCESSES[rand.nextInt(FAKE_PROCESSES.length)],
                    "user", FAKE_CREDENTIALS[rand.nextInt(FAKE_CREDENTIALS.length)][0],
                    "cpu", String.format("%.1f", rand.nextDouble() * 50),
                    "mem", String.format("%.1f", rand.nextDouble() * 30)
                ));
            }
            return Map.of(
                "status", "success",
                "processes", processes,
                "count", processes.size()
            );
        }

        if (cmdLower.contains("ifconfig") || cmdLower.contains("ip")) {
            return Map.of(
                "status", "success",
                "interfaces", Arrays.asList(
                    Map.of("name", "eth0", "ip", "10.0.0." + (50 + rand.nextInt(50)), "mask", "255.255.255.0"),
                    Map.of("name", "lo", "ip", "127.0.0.1", "mask", "255.0.0.0")
                )
            );
        }

        if (cmdLower.contains("netstat") || cmdLower.contains("ss")) {
            List<Map<String, Object>> connections = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                connections.add(Map.of(
                    "protocol", i % 2 == 0 ? "TCP" : "UDP",
                    "local", "10.0.0." + (50 + rand.nextInt(50)) + ":" + (1024 + rand.nextInt(50000)),
                    "remote", "0.0.0.0:0",
                    "state", i % 3 == 0 ? "LISTENING" : "ESTABLISHED"
                ));
            }
            return Map.of(
                "status", "success",
                "connections", connections,
                "count", connections.size()
            );
        }

        if (cmdLower.contains("uptime")) {
            int upSeconds = 3600 + rand.nextInt(86400 * 30); // 1 hour to 30 days
            int days = upSeconds / 86400;
            int hours = (upSeconds % 86400) / 3600;
            int mins = (upSeconds % 3600) / 60;
            return Map.of(
                "status", "success",
                "uptime_seconds", upSeconds,
                "uptime_formatted", String.format("%d days, %d hours, %d minutes", days, hours, mins),
                "load_average", String.format("%.2f, %.2f, %.2f", 0.5 + rand.nextDouble() * 2, 0.4 + rand.nextDouble() * 1.5, 0.3 + rand.nextDouble() * 1.2)
            );
        }

        if (cmdLower.contains("crontab")) {
            List<String> crons = Arrays.asList(
                "0 2 * * * /usr/local/bin/backup.sh",
                "*/5 * * * * /opt/monitor/health_check.sh",
                "0 */6 * * * /usr/bin/apt-get update"
            );
            return Map.of(
                "status", "success",
                "crontabs", crons,
                "count", crons.size()
            );
        }

        if (cmdLower.contains("cat") || cmdLower.contains("head") || cmdLower.contains("tail")) {
            List<String> fakeFileLines = Arrays.asList(
                "# Configuration file",
                "DATABASE_USER=postgres",
                "DATABASE_PASS=Secure#2024",
                "API_KEY=sk_live_51234567890abcdef",
                "SECRET_TOKEN=ghp_xxxxxxxxxxxxxxxxxxxx"
            );
            return Map.of(
                "status", "success",
                "content", fakeFileLines,
                "lines", fakeFileLines.size(),
                "result", String.join("\n", fakeFileLines)
            );
        }

        if (cmdLower.contains("curl") || cmdLower.contains("wget") || cmdLower.contains("download")) {
            return Map.of(
                "status", "success",
                "message", "Download initiated",
                "bytes_transferred", 1024 + rand.nextInt(1000000),
                "url", "http://internal-repo.local/payload.tar.gz",
                "destination", "/tmp/download_" + (1000 + rand.nextInt(9000))
            );
        }

        if (cmdLower.contains("ssh")) {
            return Map.of(
                "status", "success",
                "message", "SSH connection established",
                "host", "10.0.0." + (10 + rand.nextInt(240)),
                "port", 22,
                "user", FAKE_CREDENTIALS[rand.nextInt(FAKE_CREDENTIALS.length)][0]
            );
        }

        // Generic fallback response
        return Map.of(
            "status", "success",
            "message", "Command executed",
            "result", "Operation completed successfully",
            "exit_code", 0,
            "timestamp", System.currentTimeMillis()
        );
    }

    /**
     * Generate fake credentials as if accessed via honeypot.
     */
    public static Map<String, Object> generateFakeCredentials() {
        Random rand = new Random(System.currentTimeMillis() % 1000); // Semi-random
        String[] creds = FAKE_CREDENTIALS[rand.nextInt(FAKE_CREDENTIALS.length)];
        return Map.of(
            "status", "success",
            "credentials", Arrays.asList(
                Map.of("user", creds[0], "password", creds[1]),
                Map.of("user", "root", "ssh_key", "-----BEGIN RSA PRIVATE KEY-----\nMIIEpAIBAAKCAQEA..." + rand.nextLong())
            ),
            "count", 2
        );
    }

    /**
     * Generate fake system information.
     */
    public static Map<String, Object> generateFakeSystemInfo() {
        Random rand = new Random();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "success");
        result.put("hostname", FAKE_HOSTNAMES[rand.nextInt(FAKE_HOSTNAMES.length)]);
        result.put("os", "Linux");
        result.put("kernel_version", "5." + (15 + rand.nextInt(5)) + ".0");
        result.put("uptime_seconds", 86400 * (7 + rand.nextInt(30)));
        result.put("total_memory_mb", 16384);
        result.put("available_memory_mb", 2048 + rand.nextInt(8192));
        result.put("disk_total_gb", 512);
        result.put("disk_free_gb", 50 + rand.nextInt(200));
        result.put("cpu_count", 4 + rand.nextInt(16));
        result.put("load_average", String.format("%.2f", 0.5 + rand.nextDouble() * 2));
        return result;
    }
}
