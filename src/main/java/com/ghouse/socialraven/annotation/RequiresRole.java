package com.ghouse.socialraven.annotation;

import com.ghouse.socialraven.constant.WorkspaceRole;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares the minimum WorkspaceRole required to invoke a controller method.
 * Enforced by WorkspaceRoleAspect, which reads the caller's role from WorkspaceContext.
 *
 * Only applies to endpoints that go through WorkspaceAccessFilter (i.e. all routes
 * that require an X-Workspace-Id header, excluding /workspaces/** and /public/**).
 *
 * Example:
 *   @RequiresRole(WorkspaceRole.MEMBER)  // MEMBER, ADMIN, or OWNER can proceed
 *   @PostMapping("/schedule")
 *   public PostCollection schedulePost(...) { ... }
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresRole {
    WorkspaceRole value();
}
