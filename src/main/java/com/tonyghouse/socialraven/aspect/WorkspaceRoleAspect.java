package com.tonyghouse.socialraven.aspect;

import com.tonyghouse.socialraven.annotation.RequiresRole;
import com.tonyghouse.socialraven.constant.WorkspaceRole;
import com.tonyghouse.socialraven.exception.SocialRavenException;
import com.tonyghouse.socialraven.util.WorkspaceContext;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Enforces @RequiresRole on controller methods.
 *
 * Reads the caller's WorkspaceRole from WorkspaceContext (populated by WorkspaceAccessFilter)
 * and throws 403 if the caller's role is below the required minimum.
 */
@Aspect
@Component
public class WorkspaceRoleAspect {

    @Before("@annotation(requiresRole)")
    public void checkRole(RequiresRole requiresRole) {
        WorkspaceRole callerRole = WorkspaceContext.getRole();
        if (callerRole == null || !callerRole.isAtLeast(requiresRole.value())) {
            throw new SocialRavenException(
                    "Insufficient role — requires " + requiresRole.value() + " or higher",
                    HttpStatus.FORBIDDEN
            );
        }
    }
}
