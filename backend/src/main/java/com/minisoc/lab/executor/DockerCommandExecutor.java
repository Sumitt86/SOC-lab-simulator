package com.minisoc.lab.executor;

import com.minisoc.lab.model.CommandResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Docker-based implementation of SystemCommandExecutor.
 * Uses `docker exec` to run commands inside named containers.
 *
 * The "host" parameter is interpreted as the Docker container name.
 * Requires the Docker socket to be mounted into the backend container
 * and the Docker CLI to be available.
 */
@Primary
@Component
@ConditionalOnProperty(name = "cyber-range.mode", havingValue = "docker", matchIfMissing = true)
public class DockerCommandExecutor implements SystemCommandExecutor {

    private static final Logger log = LoggerFactory.getLogger(DockerCommandExecutor.class);
    private static final int DEFAULT_TIMEOUT_SECONDS = 15;

    @Override
    public CommandResult execute(String containerName, String command) {
        long start = System.currentTimeMillis();
        List<String> cmd = buildDockerExecCommand(containerName, command);

        log.info("DOCKER EXEC [{}]: {}", containerName, command);

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

            boolean completed = process.waitFor(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            long duration = System.currentTimeMillis() - start;

            if (!completed) {
                process.destroyForcibly();
                log.warn("DOCKER EXEC TIMEOUT [{}]: {} ({}ms)", containerName, command, duration);
                return CommandResult.error(containerName, command,
                        "Docker exec timed out after " + DEFAULT_TIMEOUT_SECONDS + "s");
            }

            int exitCode = process.exitValue();
            if (exitCode == 0) {
                log.debug("DOCKER EXEC OK [{}]: {} ({}ms)", containerName, command, duration);
                return CommandResult.success(containerName, command, stdout, duration);
            } else {
                log.warn("DOCKER EXEC FAIL [{}]: {} exit={} stderr={}", containerName, command, exitCode, stderr);
                return CommandResult.failure(containerName, command, exitCode, stderr, duration);
            }

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            log.error("DOCKER EXEC ERROR [{}]: {} — {}", containerName, command, e.getMessage());
            return CommandResult.error(containerName, command, e.getMessage());
        }
    }

    @Override
    public CommandResult executeAsync(String containerName, String command) {
        // For async: use docker exec -d (detached mode)
        long start = System.currentTimeMillis();
        List<String> cmd = buildDockerExecDetachedCommand(containerName, command);

        log.info("DOCKER EXEC ASYNC [{}]: {}", containerName, command);

        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            String output;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                output = reader.lines().collect(Collectors.joining("\n"));
            }

            boolean completed = process.waitFor(10, TimeUnit.SECONDS);
            long duration = System.currentTimeMillis() - start;

            if (!completed) {
                process.destroyForcibly();
            }

            int exitCode = completed ? process.exitValue() : 0;
            if (exitCode == 0 || !completed) {
                return CommandResult.success(containerName, command, output, duration);
            } else {
                return CommandResult.failure(containerName, command, exitCode, output, duration);
            }

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            log.error("DOCKER EXEC ASYNC ERROR [{}]: {} — {}", containerName, command, e.getMessage());
            return CommandResult.error(containerName, command, e.getMessage());
        }
    }

    @Override
    public boolean testConnection(String containerName) {
        CommandResult result = execute(containerName, "echo ok");
        return result.success() && "ok".equals(result.stdout().trim());
    }

    private List<String> buildDockerExecCommand(String containerName, String command) {
        List<String> cmd = new ArrayList<>();
        cmd.add("docker");
        cmd.add("exec");
        cmd.add(containerName);
        cmd.add("bash");
        cmd.add("-c");
        cmd.add(command);
        return cmd;
    }

    private List<String> buildDockerExecDetachedCommand(String containerName, String command) {
        List<String> cmd = new ArrayList<>();
        cmd.add("docker");
        cmd.add("exec");
        cmd.add("-d");
        cmd.add(containerName);
        cmd.add("bash");
        cmd.add("-c");
        cmd.add(command);
        return cmd;
    }
}
