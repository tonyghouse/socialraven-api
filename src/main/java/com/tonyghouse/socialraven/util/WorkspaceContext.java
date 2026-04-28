package com.tonyghouse.socialraven.util;

import com.tonyghouse.socialraven.constant.WorkspaceRole;

/**
 * ThreadLocal holder for the current request's resolved workspace ID and role.
 * Populated by WorkspaceAccessFilter and cleared after the request completes.
 */
public class WorkspaceContext {

    private WorkspaceContext() {}

    private static final ThreadLocal<String> workspaceIdHolder = new ThreadLocal<>();
    private static final ThreadLocal<WorkspaceRole> roleHolder = new ThreadLocal<>();

    public static void set(String workspaceId, WorkspaceRole role) {
        workspaceIdHolder.set(workspaceId);
        roleHolder.set(role);
    }

    public static String getWorkspaceId() {
        return workspaceIdHolder.get();
    }

    public static WorkspaceRole getRole() {
        return roleHolder.get();
    }

    public static void clear() {
        workspaceIdHolder.remove();
        roleHolder.remove();
    }
}
