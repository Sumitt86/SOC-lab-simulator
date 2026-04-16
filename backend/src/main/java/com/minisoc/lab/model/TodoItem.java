package com.minisoc.lab.model;

public record TodoItem(
        long id,
        String team,
        int phase,
        String title,
        String priority,
        boolean done
) {
}
