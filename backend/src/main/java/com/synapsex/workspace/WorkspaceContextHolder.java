package com.synapsex.workspace;

import java.util.UUID;

public class WorkspaceContextHolder {

    private static final ThreadLocal<UUID> CONTEXT = new ThreadLocal<>();

    public static void setWorkspaceId(UUID workspaceId) {
        CONTEXT.set(workspaceId);
    }

    public static UUID getWorkspaceId() {
        return CONTEXT.get();
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
