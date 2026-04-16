package com.minisoc.lab.executor;

import com.minisoc.lab.model.CommandResult;

/**
 * Abstraction for executing system commands on remote hosts.
 * Today: Process-based SSH.  Tomorrow: JSch / SSHJ library.
 */
public interface SystemCommandExecutor {

    /**
     * Execute a command synchronously on the given host and wait for completion.
     */
    CommandResult execute(String host, String command);

    /**
     * Execute a command asynchronously (fire-and-forget) on the given host.
     * Used for attack scripts that should run in background.
     */
    CommandResult executeAsync(String host, String command);

    /**
     * Test connectivity to a host.
     */
    boolean testConnection(String host);
}
