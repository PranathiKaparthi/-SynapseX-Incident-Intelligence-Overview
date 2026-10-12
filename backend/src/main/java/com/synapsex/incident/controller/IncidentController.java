package com.synapsex.incident.controller;

import com.synapsex.auth.security.UserPrincipal;
import com.synapsex.incident.model.Incident;
import com.synapsex.incident.model.IncidentEvent;
import com.synapsex.incident.model.IncidentStatus;
import com.synapsex.incident.service.IncidentService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/incidents")
public class IncidentController {

    private final IncidentService incidentService;

    public IncidentController(IncidentService incidentService) {
        this.incidentService = incidentService;
    }

    @GetMapping
    public ResponseEntity<Page<Incident>> listIncidents(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "startedAt") String sort) {

        UUID workspaceId = principal != null && principal.getWorkspaceId() != null
                ? principal.getWorkspaceId()
                : UUID.fromString("00000000-0000-0000-0000-000000000001");

        return ResponseEntity.ok(incidentService.listIncidents(
                workspaceId, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, sort))));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Incident> getIncident(@PathVariable UUID id) {
        return ResponseEntity.ok(incidentService.getIncident(id));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<Incident> updateStatus(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody Map<String, String> body) {

        IncidentStatus status = IncidentStatus.valueOf(body.get("status"));
        String note = body.get("note");
        String actor = principal != null ? principal.getUsername() : "ENGINEER";

        return ResponseEntity.ok(incidentService.updateStatus(id, status, actor, note));
    }

    @PostMapping("/{id}/assign")
    public ResponseEntity<Void> assignUser(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody Map<String, String> body) {

        UUID userId = UUID.fromString(body.get("userId"));
        UUID actorId = principal != null ? principal.getId() : null;

        incidentService.assignUser(id, userId, actorId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}/timeline")
    public ResponseEntity<List<IncidentEvent>> getTimeline(@PathVariable UUID id) {
        return ResponseEntity.ok(incidentService.getTimeline(id));
    }
}
