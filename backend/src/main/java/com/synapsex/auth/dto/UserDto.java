package com.synapsex.auth.dto;

import com.synapsex.auth.model.SystemRole;
import java.util.UUID;

public record UserDto(
    UUID id,
    String email,
    String fullName,
    SystemRole role
) {}
