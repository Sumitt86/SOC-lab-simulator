package com.minisoc.lab.model;

/**
 * Result of executing a system command via SSH or local exec.
 */
public record CommandResult(
        boolean success,
        int exitCode,
        String stdout,
        String stderr,
        String host,
        String command,
        long durationMs
) {
    public static CommandResult success(String host, String command, String stdout, long durationMs) {
        return new CommandResult(true, 0, stdout, "", host, command, durationMs);
    }

    public static CommandResult failure(String host, String command, int exitCode, String stderr, long durationMs) {
        return new CommandResult(false, exitCode, "", stderr, host, command, durationMs);
    }

    public static CommandResult error(String host, String command, String errorMsg) {
        return new CommandResult(false, -1, "", errorMsg, host, command, 0);
    }
}
