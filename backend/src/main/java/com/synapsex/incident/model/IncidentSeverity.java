package com.synapsex.incident.model;

public enum IncidentSeverity {
    SEV1, // Critical revenue/customer impacting, multiple tier-1 services
    SEV2, // Significant impairment on tier-1 service
    SEV3, // Degraded performance or non-critical service impairment
    SEV4; // Low impact or isolated warning

    public static IncidentSeverity calculate(int impactedServicesCount, boolean hasTier1Service, boolean hasCriticalAnomaly) {
        if (impactedServicesCount >= 3 && hasTier1Service) {
            return SEV1;
        }
        if (hasCriticalAnomaly || (impactedServicesCount >= 2 && hasTier1Service)) {
            return SEV2;
        }
        if (impactedServicesCount >= 1) {
            return SEV3;
        }
        return SEV4;
    }
}
