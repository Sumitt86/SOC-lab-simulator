package com.minisoc.lab.executor;

import com.minisoc.lab.model.CommandResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * SSH-based implementation of SystemCommandExecutor.
 * Uses the system's ssh binary with key-based auth.
 *
 * Configuration via application.properties:
 *   cyber-range.ssh.key-path     = /path/to/private/key
 *   cyber-range.ssh.user         = soc_agent
 *   cyber-range.ssh.timeout      = 10
 *   cyber-range.victim.ip        = 192.168.56.101
 *   cyber-range.kali.ip          = 192.168.56.100
 */
@Component
@ConditionalOnProperty(name = "cyber-range.mode", havingValue = "ssh")
public class SshCommandExecutor implements SystemCommandExecutor {

    private static final Logger log = LoggerFactory.getLogger(SshCommandExecutor.class);

    @Value("${cyber-range.ssh.key-path:}")
    private String sshKeyPath;

    @Value("${cyber-range.ssh.user:soc_agent}")
    private String sshUser;

    @Value("${cyber-range.ssh.timeout:10}")
    private int sshTimeoutSeconds;

    @Override
    public CommandResult execute(String host, String command) {
        long start = System.currentTimeMillis();
        List<String> cmd = buildSshCommand(host, command);

        log.info("SSH EXEC [{}]: {}", host, command);

        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(false);
            Process process = pb.start();

            String stdout;
            String stderr;
            try (BufferedReader outReader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
                 BufferedReader errReader = new BufferedReader(
                         new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {

                stdout = outReader.lines().collect(Collectors.joining("\n"));
                stderr = errReader.lines().collect(Collectors.joining("\n"));
            }

            boolean completed = process.waitFor(sshTimeoutSeconds, TimeUnit.SECONDS);
            long duration = System.currentTimeMillis() - start;

            if (!completed) {
                process.destroyForcibly();
                log.warn("SSH TIMEOUT [{}]: {} ({}ms)", host, command, duration);
                return CommandResult.error(host, command, "SSH command timed out after " + sshTimeoutSeconds + "s");
            }

            int exitCode = process.exitValue();
            if (exitCode == 0) {
                log.debug("SSH OK [{}]: {} ({}ms)", host, command, duration);
                return CommandResult.success(host, command, stdout, duration);
            } else {
                log.warn("SSH FAIL [{}]: {} exit={} stderr={}", host, command, exitCode, stderr);
                return CommandResult.failure(host, command, exitCode, stderr, duration);
            }

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            log.error("SSH ERROR [{}]: {} — {}", host, command, e.getMessage());
            return CommandResult.error(host, command, e.getMessage());
        }
    }

    @Override
    public CommandResult executeAsync(String host, String command) {
        // For async: append nohup + & and don't wait for full output
        String asyncCommand = "nohup " + command + " > /dev/null 2>&1 &";
        return execute(host, asyncCommand);
    }

    @Override
    public boolean testConnection(String host) {
        CommandResult result = execute(host, "echo ok");
        return result.success() && "ok".equals(result.stdout().trim());
    }

    private List<String> buildSshCommand(String host, String command) {
        List<String> cmd = new ArrayList<>();
        cmd.add("ssh");
        cmd.add("-o");
        cmd.add("StrictHostKeyChecking=no");
        cmd.add("-o");
        cmd.add("ConnectTimeout=" + sshTimeoutSeconds);
        cmd.add("-o");
        cmd.add("BatchMode=yes");

        if (sshKeyPath != null && !sshKeyPath.isBlank()) {
            cmd.add("-i");
            cmd.add(sshKeyPath);
        }

        cmd.add(sshUser + "@" + host);
        cmd.add(command);
        return cmd;
    }
}
