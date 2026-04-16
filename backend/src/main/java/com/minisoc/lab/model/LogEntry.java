package com.minisoc.lab.model;

public record LogEntry(
        long id,
        String timestamp,
        String severity,
        String message
) {
}
