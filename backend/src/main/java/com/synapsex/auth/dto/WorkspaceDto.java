package com.synapsex.auth.dto;

import java.util.UUID;

public record WorkspaceDto(
    UUID id,
    String name,
    String slug
) {}
