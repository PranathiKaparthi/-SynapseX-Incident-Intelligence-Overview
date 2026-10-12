package com.synapsex.incident;

import com.synapsex.incident.model.*;
import com.synapsex.incident.repository.*;
import com.synapsex.incident.service.CorrelationEngine;
import com.synapsex.incident.service.IncidentService;
import com.synapsex.processing.model.Anomaly;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CorrelationAndLifecycleTest {

    @Mock private IncidentRepository incidentRepository;
    @Mock private IncidentEventRepository incidentEventRepository;
    @Mock private IncidentAssignmentRepository assignmentRepository;
    @Mock private ServiceRepository serviceRepository;
    @Mock private ServiceDependencyRepository dependencyRepository;

    private CorrelationEngine correlationEngine;
    private IncidentService incidentService;

    private UUID workspaceId;
    private MonitoredService svcPostgres;
    private MonitoredService svcPayment;
    private MonitoredService svcOrder;
    private MonitoredService svcInventory;

    @BeforeEach
    void setUp() {
        correlationEngine = new CorrelationEngine(
                incidentRepository,
                incidentEventRepository,
                serviceRepository,
                dependencyRepository
        );

        incidentService = new IncidentService(
                incidentRepository,
                incidentEventRepository,
                assignmentRepository,
                serviceRepository
        );

        workspaceId = UUID.randomUUID();

        UUID idPg = UUID.randomUUID();
        UUID idPay = UUID.randomUUID();
        UUID idOrd = UUID.randomUUID();
        UUID idInv = UUID.randomUUID();

        svcPostgres = new MonitoredService(idPg, workspaceId, "postgres-db", "prod", "TIER_1", "HEALTHY");
        svcPayment = new MonitoredService(idPay, workspaceId, "payment-service", "prod", "TIER_1", "HEALTHY");
        svcOrder = new MonitoredService(idOrd, workspaceId, "order-service", "prod", "TIER_1", "HEALTHY");
        svcInventory = new MonitoredService(idInv, workspaceId, "inventory-service", "prod", "TIER_2", "HEALTHY");

        // Dependencies: order -> payment -> postgres; inventory is independent in this test
        List<ServiceDependency> deps = List.of(
                new ServiceDependency(workspaceId, idOrd, idPay, "SYNCHRONOUS_HTTP"),
                new ServiceDependency(workspaceId, idPay, idPg, "DATABASE")
        );

        lenient().when(dependencyRepository.findByWorkspaceId(workspaceId)).thenReturn(deps);
        lenient().when(serviceRepository.findByWorkspaceIdAndName(workspaceId, "postgres-db")).thenReturn(Optional.of(svcPostgres));
        lenient().when(serviceRepository.findByWorkspaceIdAndName(workspaceId, "payment-service")).thenReturn(Optional.of(svcPayment));
        lenient().when(serviceRepository.findByWorkspaceIdAndName(workspaceId, "order-service")).thenReturn(Optional.of(svcOrder));
        lenient().when(serviceRepository.findByWorkspaceIdAndName(workspaceId, "inventory-service")).thenReturn(Optional.of(svcInventory));
    }

    @Test
    @DisplayName("Cascading failure (DB -> Payment -> Order) yields ONE incident; unrelated inventory CPU yields a SEPARATE one")
    void testCascadingVsIsolatedCorrelation() {
        Instant t0 = Instant.parse("2026-10-12T10:25:00Z");
        Instant t1 = Instant.parse("2026-10-12T10:27:00Z");
        Instant t2 = Instant.parse("2026-10-12T10:28:00Z");
        Instant t3 = Instant.parse("2026-10-12T10:30:00Z");

        // 1. 10:25 - Anomaly on postgres-db
        Anomaly aDb = new Anomaly(workspaceId, null, null, "postgres-db", "dbConnUtilization", "CRITICAL", t0, 98.0, 95.0, "{}");

        when(incidentRepository.findByWorkspaceIdAndStatus(workspaceId, IncidentStatus.DETECTED))
                .thenReturn(new ArrayList<>());
        when(incidentRepository.save(any(Incident.class))).thenAnswer(i -> i.getArgument(0));

        Incident inc1 = correlationEngine.correlateAnomaly(aDb);
        assertNotNull(inc1);
        assertEquals("postgres-db", inc1.getRootCauseServiceName());

        // 2. 10:27 - Anomaly on payment-service (depends directly on postgres-db)
        Anomaly aPayment = new Anomaly(workspaceId, null, null, "payment-service", "latencyMs", "CRITICAL", t1, 3500.0, 2000.0, "{}");

        // open incidents now include inc1
        when(incidentRepository.findByWorkspaceIdAndStatus(workspaceId, IncidentStatus.DETECTED))
                .thenReturn(new ArrayList<>(List.of(inc1)));

        Incident correlatedPayment = correlationEngine.correlateAnomaly(aPayment);
        assertSame(inc1, correlatedPayment, "Payment anomaly should correlate into existing incident #1");

        // 3. 10:28 - Anomaly on order-service (depends directly on payment-service)
        Anomaly aOrder = new Anomaly(workspaceId, null, null, "order-service", "errorRate", "CRITICAL", t2, 15.0, 10.0, "{}");

        Incident correlatedOrder = correlationEngine.correlateAnomaly(aOrder);
        assertSame(inc1, correlatedOrder, "Order anomaly should correlate into existing incident #1");

        // 4. 10:30 - Simultaneous anomaly on unrelated inventory-service
        Anomaly aInventory = new Anomaly(workspaceId, null, null, "inventory-service", "cpuUsage", "CRITICAL", t3, 95.0, 90.0, "{}");

        Incident inc2 = correlationEngine.correlateAnomaly(aInventory);
        assertNotNull(inc2);
        assertNotSame(inc1, inc2, "Unrelated inventory anomaly must create a separate incident!");
        assertEquals("inventory-service", inc2.getRootCauseServiceName());
    }

    @Test
    @DisplayName("State machine transitions: validate permitted transitions, reopening, and rejection of illegal transitions")
    void testStateMachineTransitions() {
        Incident incident = new Incident(
                workspaceId, "Test Incident", "Summary",
                IncidentStatus.DETECTED, IncidentSeverity.SEV2, "corr-test", "order-service", Instant.now());
        UUID id = UUID.randomUUID();

        when(incidentRepository.findById(id)).thenReturn(Optional.of(incident));
        when(incidentRepository.save(any(Incident.class))).thenAnswer(i -> i.getArgument(0));

        // DETECTED -> INVESTIGATING (Allowed)
        Incident updated = incidentService.updateStatus(id, IncidentStatus.INVESTIGATING, "lead", "Starting investigation");
        assertEquals(IncidentStatus.INVESTIGATING, updated.getStatus());

        // INVESTIGATING -> MITIGATING (Allowed)
        updated = incidentService.updateStatus(id, IncidentStatus.MITIGATING, "lead", "Applying workaround");
        assertEquals(IncidentStatus.MITIGATING, updated.getStatus());

        // MITIGATING -> RESOLVED (Allowed)
        updated = incidentService.updateStatus(id, IncidentStatus.RESOLVED, "lead", "Incident resolved");
        assertEquals(IncidentStatus.RESOLVED, updated.getStatus());
        assertNotNull(updated.getResolvedAt());

        // RESOLVED -> INVESTIGATING (Reopening Allowed)
        updated = incidentService.updateStatus(id, IncidentStatus.INVESTIGATING, "lead", "Reopening due to recurrence");
        assertEquals(IncidentStatus.INVESTIGATING, updated.getStatus());
        assertNull(updated.getResolvedAt(), "Resolved timestamp cleared on reopen");

        // INVESTIGATING -> DETECTED (Illegal Transition!)
        assertThrows(IllegalArgumentException.class, () ->
                incidentService.updateStatus(id, IncidentStatus.DETECTED, "lead", "Illegal backward jump"));
    }
}
