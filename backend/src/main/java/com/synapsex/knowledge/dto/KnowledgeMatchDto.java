package com.synapsex.knowledge.dto;

import java.util.UUID;

public record KnowledgeMatchDto(
    UUID knowledgeId,
    UUID incidentId,
    String title,
    String summary,
    String rootCause,
    String resolutionProcedure,
    double similarity
) {}
