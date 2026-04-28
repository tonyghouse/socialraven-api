package com.tonyghouse.socialraven.config;

import com.tonyghouse.socialraven.constant.WorkspaceRole;
import com.tonyghouse.socialraven.util.SecurityContextUtil;
import com.tonyghouse.socialraven.util.WorkspaceContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Runs after ClerkAuthenticationFilter.
 * Resolves the workspace for the current request from the X-Workspace-Id header.
 * DB-backed role resolution will be wired when the workspace feature is implemented.
 */
@Component
public class WorkspaceAccessFilter extends OncePerRequestFilter {

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return path.startsWith("/public/") || path.startsWith("/admin/")
                || path.startsWith("/workspaces") || path.startsWith("/onboarding")
                || "OPTIONS".equalsIgnoreCase(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            filterChain.doFilter(request, response);
            return;
        }

        String userId = SecurityContextUtil.getUserId(SecurityContextHolder.getContext());
        if (userId == null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String requestedWorkspaceId = request.getHeader("X-Workspace-Id");
            if (requestedWorkspaceId == null || requestedWorkspaceId.isBlank()) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            // TODO: replace with DB-backed role lookup when workspace feature is implemented
            WorkspaceContext.set(requestedWorkspaceId, WorkspaceRole.OWNER);
            filterChain.doFilter(request, response);
        } finally {
            WorkspaceContext.clear();
        }
    }
}
