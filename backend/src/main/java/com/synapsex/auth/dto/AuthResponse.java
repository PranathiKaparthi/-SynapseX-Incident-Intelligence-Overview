package com.synapsex.auth.dto;

public record AuthResponse(
    UserDto user,
    WorkspaceDto workspace,
    String accessToken,
    String refreshToken
) {}
