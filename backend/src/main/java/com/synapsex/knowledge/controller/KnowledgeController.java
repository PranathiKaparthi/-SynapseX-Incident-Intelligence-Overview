package com.synapsex.knowledge.controller;

import com.synapsex.auth.security.UserPrincipal;
import com.synapsex.knowledge.dto.CreateKnowledgeDto;
import com.synapsex.knowledge.dto.KnowledgeMatchDto;
import com.synapsex.knowledge.model.IncidentKnowledge;
import com.synapsex.knowledge.repository.IncidentKnowledgeRepository;
import com.synapsex.knowledge.service.RagService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {

    private final RagService ragService;
    private final IncidentKnowledgeRepository knowledgeRepository;

    public KnowledgeController(
            RagService ragService,
            IncidentKnowledgeRepository knowledgeRepository) {
        this.ragService = ragService;
        this.knowledgeRepository = knowledgeRepository;
    }

    @GetMapping("/search")
    public ResponseEntity<List<KnowledgeMatchDto>> searchSimilar(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam String query,
            @RequestParam(defaultValue = "0.70") double cutoff) {

        UUID workspaceId = principal != null && principal.getWorkspaceId() != null
                ? principal.getWorkspaceId()
                : UUID.fromString("00000000-0000-0000-0000-000000000001");

        return ResponseEntity.ok(ragService.searchSimilar(workspaceId, query, cutoff));
    }

    @PostMapping
    public ResponseEntity<IncidentKnowledge> createKnowledge(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateKnowledgeDto dto) {

        UUID workspaceId = principal != null && principal.getWorkspaceId() != null
                ? principal.getWorkspaceId()
                : UUID.fromString("00000000-0000-0000-0000-000000000001");

        IncidentKnowledge created = ragService.indexResolvedIncident(workspaceId, dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    public ResponseEntity<List<IncidentKnowledge>> listKnowledge(
            @AuthenticationPrincipal UserPrincipal principal) {

        UUID workspaceId = principal != null && principal.getWorkspaceId() != null
                ? principal.getWorkspaceId()
                : UUID.fromString("00000000-0000-0000-0000-000000000001");

        return ResponseEntity.ok(knowledgeRepository.findByWorkspaceId(workspaceId));
    }
}
