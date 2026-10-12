package com.synapsex.incident.model;

public enum IncidentStatus {
    DETECTED,
    INVESTIGATING,
    MITIGATING,
    RESOLVED,
    CLOSED;

    public boolean canTransitionTo(IncidentStatus target) {
        if (this == target) return true;
        return switch (this) {
            case DETECTED -> target == INVESTIGATING || target == MITIGATING || target == CLOSED;
            case INVESTIGATING -> target == MITIGATING || target == RESOLVED || target == CLOSED;
            case MITIGATING -> target == INVESTIGATING || target == RESOLVED || target == CLOSED;
            case RESOLVED -> target == INVESTIGATING || target == CLOSED; // Reopening allowed
            case CLOSED -> target == INVESTIGATING; // Reopening allowed
        };
    }
}
