package com.minisoc.lab.model;

public record AlertItem(
        long id,
        String severity,
        String title,
        String detail,
        String mitreTag,
        String timestamp,
        boolean open
) {
}
