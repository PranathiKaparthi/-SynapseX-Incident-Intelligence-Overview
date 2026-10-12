package com.synapsex.auth.security;

import com.synapsex.auth.model.SystemRole;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service("workspaceSecurity")
public class WorkspaceSecurityService {

    public boolean canAccess(Authentication authentication, UUID targetWorkspaceId) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            return false;
        }

        // Global admin can access any workspace
        if (principal.getRole() == SystemRole.ADMIN) {
            return true;
        }

        // Scoped tenant match
        return principal.getWorkspaceId() != null && principal.getWorkspaceId().equals(targetWorkspaceId);
    }

    public boolean hasRole(Authentication authentication, String roleName) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            return false;
        }

        return principal.getRole().name().equalsIgnoreCase(roleName) || principal.getRole() == SystemRole.ADMIN;
    }
}
