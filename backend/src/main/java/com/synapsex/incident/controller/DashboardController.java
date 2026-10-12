package com.synapsex.incident.controller;

import com.synapsex.auth.security.UserPrincipal;
import com.synapsex.incident.model.MonitoredService;
import com.synapsex.incident.repository.ServiceRepository;
import com.synapsex.incident.service.IncidentService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final IncidentService incidentService;
    private final ServiceRepository serviceRepository;

    public DashboardController(IncidentService incidentService, ServiceRepository serviceRepository) {
        this.incidentService = incidentService;
        this.serviceRepository = serviceRepository;
    }

    @GetMapping("/overview")
    public ResponseEntity<Map<String, Object>> getOverview(@AuthenticationPrincipal UserPrincipal principal) {
        UUID workspaceId = principal != null && principal.getWorkspaceId() != null
                ? principal.getWorkspaceId()
                : UUID.fromString("00000000-0000-0000-0000-000000000001");

        return ResponseEntity.ok(incidentService.getDashboardOverview(workspaceId));
    }

    @GetMapping("/services")
    public ResponseEntity<List<MonitoredService>> getServices(@AuthenticationPrincipal UserPrincipal principal) {
        UUID workspaceId = principal != null && principal.getWorkspaceId() != null
                ? principal.getWorkspaceId()
                : UUID.fromString("00000000-0000-0000-0000-000000000001");

        return ResponseEntity.ok(serviceRepository.findByWorkspaceId(workspaceId));
    }
}
