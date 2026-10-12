package com.synapsex.ai.controller;

import com.synapsex.ai.dto.InvestigationResultDto;
import com.synapsex.ai.service.AiOrchestratorService;
import com.synapsex.auth.security.UserPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/incidents/{incidentId}")
public class AiInvestigationController {

    private final AiOrchestratorService orchestratorService;

    public AiInvestigationController(AiOrchestratorService orchestratorService) {
        this.orchestratorService = orchestratorService;
    }

    @PostMapping("/investigate")
    public ResponseEntity<InvestigationResultDto> triggerInvestigation(
            @PathVariable UUID incidentId,
            @AuthenticationPrincipal UserPrincipal principal) {

        UUID workspaceId = principal != null && principal.getWorkspaceId() != null
                ? principal.getWorkspaceId()
                : UUID.fromString("00000000-0000-0000-0000-000000000001");

        return ResponseEntity.ok(orchestratorService.runInvestigation(incidentId, workspaceId));
    }

    @GetMapping("/investigation")
    public ResponseEntity<InvestigationResultDto> getInvestigation(
            @PathVariable UUID incidentId,
            @AuthenticationPrincipal UserPrincipal principal) {

        return orchestratorService.getLatestInvestigation(incidentId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> {
                    UUID workspaceId = principal != null && principal.getWorkspaceId() != null
                            ? principal.getWorkspaceId()
                            : UUID.fromString("00000000-0000-0000-0000-000000000001");
                    return ResponseEntity.ok(orchestratorService.runInvestigation(incidentId, workspaceId));
                });
    }
}
