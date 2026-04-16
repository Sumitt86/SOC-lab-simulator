package com.minisoc.lab.model;

public enum AttackPhase {
    INITIAL_ACCESS("Initial Access"),
    PERSISTENCE("Persistence"),
    LATERAL_MOVEMENT("Lateral Movement"),
    EXFILTRATION("Exfiltration"),
    CONTAINED("Contained");

    private final String displayName;

    AttackPhase(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public AttackPhase next() {
        return switch (this) {
            case INITIAL_ACCESS -> PERSISTENCE;
            case PERSISTENCE -> LATERAL_MOVEMENT;
            case LATERAL_MOVEMENT -> EXFILTRATION;
            default -> this;
        };
    }

    public int getLevel() {
        return ordinal() + 1;
    }
}
