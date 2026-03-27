package com.tonyghouse.socialraven.config;

import com.tonyghouse.socialraven.entity.WorkspaceMemberEntity;
import com.tonyghouse.socialraven.repo.WorkspaceMemberRepo;
import com.tonyghouse.socialraven.repo.WorkspaceRepo;
import com.tonyghouse.socialraven.util.SecurityContextUtil;
import com.tonyghouse.socialraven.util.WorkspaceContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * Runs after ClerkAuthenticationFilter.
 * Resolves the workspace for the current request from the X-Workspace-Id header.
 *
 * Returns 404 if the header is absent (user has no workspace yet — frontend gate redirects to onboarding).
 * Returns 403 if the header is present but the caller is not a member of that workspace.
 */
@Component
@Slf4j
public class WorkspaceAccessFilter extends OncePerRequestFilter {

    @Autowired
    private WorkspaceMemberRepo workspaceMemberRepo;

    @Autowired
    private WorkspaceRepo workspaceRepo;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        // Skip workspace management endpoints — they don't use WorkspaceContext and handle
        // their own access control. Also skip public/admin routes and preflight requests.
        return path.startsWith("/public/") || path.startsWith("/admin/")
                || path.startsWith("/workspaces") || path.startsWith("/onboarding")
                || "OPTIONS".equalsIgnoreCase(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        // Skip if not authenticated (ClerkAuthFilter handles 401)
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
                // No workspace header — user hasn't completed onboarding yet.
                // Frontend gate will redirect to onboarding.
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                return;
            }

            Optional<WorkspaceMemberEntity> membership =
                    workspaceMemberRepo.findByWorkspaceIdAndUserId(requestedWorkspaceId, userId);
            if (membership.isEmpty()) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                return;
            }

            // Reject access to soft-deleted workspaces (GDPR §5.6 — still in 30-day retention window)
            boolean workspaceActive = workspaceRepo.findByIdAndDeletedAtIsNull(requestedWorkspaceId).isPresent();
            if (!workspaceActive) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                return;
            }

            WorkspaceContext.set(requestedWorkspaceId, membership.get().getRole());
            filterChain.doFilter(request, response);
        } finally {
            WorkspaceContext.clear();
        }
    }
}
