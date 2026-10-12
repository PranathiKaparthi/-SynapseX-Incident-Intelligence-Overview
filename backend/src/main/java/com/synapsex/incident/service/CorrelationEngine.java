package com.synapsex.incident.service;

import com.synapsex.incident.model.*;
import com.synapsex.incident.repository.*;
import com.synapsex.processing.model.Anomaly;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
public class CorrelationEngine {

    private static final Logger log = LoggerFactory.getLogger(CorrelationEngine.class);

    private final IncidentRepository incidentRepository;
    private final IncidentEventRepository incidentEventRepository;
    private final ServiceRepository serviceRepository;
    private final ServiceDependencyRepository dependencyRepository;

    public CorrelationEngine(
            IncidentRepository incidentRepository,
            IncidentEventRepository incidentEventRepository,
            ServiceRepository serviceRepository,
            ServiceDependencyRepository dependencyRepository) {
        this.incidentRepository = incidentRepository;
        this.incidentEventRepository = incidentEventRepository;
        this.serviceRepository = serviceRepository;
        this.dependencyRepository = dependencyRepository;
    }

    @Transactional
    public Incident correlateAnomaly(Anomaly anomaly) {
        UUID workspaceId = anomaly.getWorkspaceId();
        String serviceName = anomaly.getServiceName();
        Instant anomalyTime = anomaly.getStartTime();

        List<Incident> openIncidents = incidentRepository.findByWorkspaceIdAndStatus(workspaceId, IncidentStatus.DETECTED);
        openIncidents.addAll(incidentRepository.findByWorkspaceIdAndStatus(workspaceId, IncidentStatus.INVESTIGATING));

        Incident bestMatch = null;
        double highestScore = 0.0;

        for (Incident inc : openIncidents) {
            // Check time proximity (within 15 minutes)
            long diffMinutes = Math.abs(Duration.between(inc.getStartedAt(), anomalyTime).toMinutes());
            if (diffMinutes > 15) {
                continue;
            }

            double score = calculateTopologicalScore(workspaceId, inc.getRootCauseServiceName(), serviceName);
            if (score > highestScore && score >= 0.7) {
                highestScore = score;
                bestMatch = inc;
            }
        }

        if (bestMatch != null) {
            // Attach and escalate existing incident
            log.info("Correlating anomaly on {} into existing incident {} (Score: {})",
                    serviceName, bestMatch.getId(), highestScore);

            escalateIncident(bestMatch, anomaly);
            return bestMatch;
        } else {
            // Create a brand new incident for this independent failure cluster
            log.info("Creating new incident for anomaly on service {}", serviceName);
            return createNewIncident(anomaly);
        }
    }

    public double calculateTopologicalScore(UUID workspaceId, String serviceA, String serviceB) {
        if (serviceA == null || serviceB == null) return 0.0;
        if (serviceA.equalsIgnoreCase(serviceB)) return 1.0;

        Optional<MonitoredService> svcOptA = serviceRepository.findByWorkspaceIdAndName(workspaceId, serviceA);
        Optional<MonitoredService> svcOptB = serviceRepository.findByWorkspaceIdAndName(workspaceId, serviceB);

        if (svcOptA.isEmpty() || svcOptB.isEmpty()) {
            // Fallback heuristic based on known default dependency graph if DB entities not initialized
            return computeHeuristicTopologyScore(serviceA, serviceB);
        }

        UUID idA = svcOptA.get().getId();
        UUID idB = svcOptB.get().getId();

        List<ServiceDependency> allDeps = dependencyRepository.findByWorkspaceId(workspaceId);

        // Build adjacency map
        Map<UUID, List<UUID>> adj = new HashMap<>();
        Set<UUID> downstreamA = new HashSet<>();
        Set<UUID> downstreamB = new HashSet<>();

        for (ServiceDependency dep : allDeps) {
            adj.computeIfAbsent(dep.getUpstreamServiceId(), k -> new ArrayList<>()).add(dep.getDownstreamServiceId());
            adj.computeIfAbsent(dep.getDownstreamServiceId(), k -> new ArrayList<>()).add(dep.getUpstreamServiceId());

            if (dep.getUpstreamServiceId().equals(idA)) downstreamA.add(dep.getDownstreamServiceId());
            if (dep.getUpstreamServiceId().equals(idB)) downstreamB.add(dep.getDownstreamServiceId());
        }

        // Check shared downstream infrastructure
        downstreamA.retainAll(downstreamB);
        if (!downstreamA.isEmpty()) {
            return 0.85;
        }

        // BFS for topological distance
        Queue<UUID> queue = new ArrayDeque<>();
        Map<UUID, Integer> dist = new HashMap<>();
        queue.add(idA);
        dist.put(idA, 0);

        while (!queue.isEmpty()) {
            UUID curr = queue.poll();
            int d = dist.get(curr);
            if (curr.equals(idB)) {
                if (d == 1) return 0.9;
                if (d == 2) return 0.85;
                if (d == 3) return 0.75;
                return 0.7;
            }
            if (d < 4) {
                for (UUID neighbor : adj.getOrDefault(curr, List.of())) {
                    if (!dist.containsKey(neighbor)) {
                        dist.put(neighbor, d + 1);
                        queue.add(neighbor);
                    }
                }
            }
        }

        // Unrelated services overlapping in time only
        return 0.2;
    }

    private double computeHeuristicTopologyScore(String a, String b) {
        // Seeded graph: postgres-db <- payment-service <- order-service <- api-gateway
        // inventory-service <- order-service
        List<String> cluster = List.of("postgres-db", "payment-service", "order-service", "api-gateway");
        if (cluster.contains(a) && cluster.contains(b)) {
            return 0.85;
        }
        return 0.2;
    }

    private void escalateIncident(Incident incident, Anomaly anomaly) {
        // Escalate severity if new critical anomaly arrives
        if ("CRITICAL".equalsIgnoreCase(anomaly.getSeverity()) && incident.getSeverity() != IncidentSeverity.SEV1) {
            incident.setSeverity(IncidentSeverity.SEV1);
        }

        String eventDesc = String.format("Anomaly on %s (%s) correlated into incident. Metric value: %s",
                anomaly.getServiceName(), anomaly.getMetricName(), anomaly.getCurrentValue());

        incidentEventRepository.save(new IncidentEvent(
                incident.getId(),
                "ANOMALY_ATTACHED",
                eventDesc,
                "CORRELATION_ENGINE",
                anomaly.getEvidenceJson()
        ));

        incidentRepository.save(incident);
    }

    private Incident createNewIncident(Anomaly anomaly) {
        String correlationKey = "corr-" + anomaly.getServiceName() + "-" + (anomaly.getStartTime().toEpochMilli() / 300000);

        // Check idempotency by correlationKey
        Optional<Incident> existing = incidentRepository.findByWorkspaceIdAndCorrelationKey(
                anomaly.getWorkspaceId(), correlationKey);
        if (existing.isPresent()) {
            return existing.get();
        }

        IncidentSeverity severity = "CRITICAL".equalsIgnoreCase(anomaly.getSeverity())
                ? IncidentSeverity.SEV2 : IncidentSeverity.SEV3;

        String title = String.format("Incident on %s: %s breach",
                anomaly.getServiceName(), anomaly.getMetricName());
        String summary = String.format("Detected %s anomaly on %s exceeding threshold %s with value %s",
                anomaly.getSeverity(), anomaly.getServiceName(), anomaly.getThresholdValue(), anomaly.getCurrentValue());

        Incident incident = new Incident(
                anomaly.getWorkspaceId(),
                title,
                summary,
                IncidentStatus.DETECTED,
                severity,
                correlationKey,
                anomaly.getServiceName(),
                anomaly.getStartTime()
        );

        incident = incidentRepository.save(incident);

        incidentEventRepository.save(new IncidentEvent(
                incident.getId(),
                "DETECTED",
                "Incident declared by Anomaly Detection Engine",
                "DETECTION_ENGINE",
                anomaly.getEvidenceJson()
        ));

        return incident;
    }
}
