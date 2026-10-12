package com.synapsex.knowledge.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public record CreateKnowledgeDto(
    UUID incidentId,
    @NotBlank(message = "Title cannot be blank")
    String title,
    @NotBlank(message = "Summary cannot be blank")
    String summary,
    @NotBlank(message = "Root cause cannot be blank")
    String rootCause,
    @NotBlank(message = "Resolution procedure cannot be blank")
    String resolutionProcedure,
    String evidenceRefsJson
) {}
