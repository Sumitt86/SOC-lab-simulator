package com.minisoc.lab.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Executes commands inside Docker containers with line-by-line SSE output streaming.
 *
 * Usage:
 *   String commandId = commandStreamService.startStream(container, command, onComplete);
 *   // client subscribes to GET /api/stream/command/{commandId}
 *   // lines are pushed to the SSE emitter as they arrive
 */
@Service
public class CommandStreamService {

    private static final Logger log = LoggerFactory.getLogger(CommandStreamService.class);
    private static final int TIMEOUT_SECONDS = 30;

    private final StreamingService streamingService;
    private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "cmd-stream");
        t.setDaemon(true);
        return t;
    });

    // commandId → exitCode (null = still running)
    private final ConcurrentHashMap<String, Integer> runningCommands = new ConcurrentHashMap<>();

    public CommandStreamService(StreamingService streamingService) {
        this.streamingService = streamingService;
    }

    /**
     * Starts streaming execution. Returns a commandId the client can subscribe to.
     *
     * @param container Docker container name
     * @param command   Shell command to run
     * @param onPoints  Callback with (exitCode, pointsEarned) when done; use null if not needed
     */
    public String startStream(String container, String command,
                              java.util.function.BiConsumer<Integer, Integer> onPoints) {
        String commandId = "CMD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        runningCommands.put(commandId, null);

        CompletableFuture.runAsync(() -> {
            List<String> cmd = List.of("docker", "exec", container, "bash", "-c", command);
            long start = System.currentTimeMillis();
            int exitCode = -1;
            try {
                ProcessBuilder pb = new ProcessBuilder(cmd);
                pb.redirectErrorStream(false);
                Process process = pb.start();

                // Stream stdout line by line
                CompletableFuture<Void> stdoutFuture = CompletableFuture.runAsync(() -> {
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            streamingService.pushCommandLine(commandId, line, "stdout");
                        }
                    } catch (Exception e) {
                        // stream closed
                    }
                }, executor);

                // Stream stderr line by line
                CompletableFuture<Void> stderrFuture = CompletableFuture.runAsync(() -> {
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            streamingService.pushCommandLine(commandId, line, "stderr");
                        }
                    } catch (Exception e) {
                        // stream closed
                    }
                }, executor);

                boolean completed = process.waitFor(TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS);
                if (!completed) {
                    process.destroyForcibly();
                    streamingService.pushCommandLine(commandId,
                            "[Command timed out after " + TIMEOUT_SECONDS + "s]", "system");
                    exitCode = 124;
                } else {
                    exitCode = process.exitValue();
                }

                stdoutFuture.join();
                stderrFuture.join();

            } catch (Exception e) {
                log.error("Command stream error [{}]: {}", commandId, e.getMessage());
                streamingService.pushCommandLine(commandId, "[Error: " + e.getMessage() + "]", "system");
                exitCode = -1;
            }

            runningCommands.put(commandId, exitCode);
            int points = 0;
            if (onPoints != null) {
                onPoints.accept(exitCode, 0); // caller fills in points after scoring
            }
            streamingService.pushCommandComplete(commandId, exitCode, points);

        }, executor);

        return commandId;
    }

    public boolean isRunning(String commandId) {
        return runningCommands.containsKey(commandId) && runningCommands.get(commandId) == null;
    }
}
