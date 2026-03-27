package com.tonyghouse.socialraven.config;

import com.clerk.backend_api.helpers.security.models.SessionAuthObjectV2;
import com.tonyghouse.socialraven.constant.UserStatus;
import com.tonyghouse.socialraven.model.ClerkAuthenticationToken;
import com.tonyghouse.socialraven.repo.UserProfileRepo;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class ClerkAuthenticationFilter extends OncePerRequestFilter {

    @Autowired
    private ClerkAuthHelper clerkAuthHelper;

    @Autowired
    private UserProfileRepo userProfileRepo;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        // Convert headers → Map<String, List<String>>
        Map<String, List<String>> requestHeaders = Collections.list(request.getHeaderNames())
                .stream()
                .collect(Collectors.toMap(
                        h -> h,
                        h -> Collections.list(request.getHeaders(h))
                ));

        try {
            SessionAuthObjectV2 auth = clerkAuthHelper.authenticate(requestHeaders);
            if (auth != null) {
                ClerkAuthenticationToken authentication =
                        new ClerkAuthenticationToken(auth, List.of(new SimpleGrantedAuthority("USER")));

                SecurityContextHolder.getContext().setAuthentication(authentication);

                // Block deactivated users unless they are re-onboarding or accepting a new invitation
                if (!isDeactivationWhitelisted(request)) {
                    String userId = auth.getSub();
                    userProfileRepo.findById(userId).ifPresent(profile -> {
                        if (profile.getStatus() == UserStatus.INACTIVE) {
                            throw new DeactivatedAccountException();
                        }
                    });
                }
            }
        } catch (DeactivatedAccountException e) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"ACCOUNT_DEACTIVATED\"}");
            return;
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Paths where INACTIVE users are still allowed through:
     *  - /onboarding/**  — so they can re-onboard and create a new workspace
     *  - /workspaces/invitations/accept — so they can accept a re-invitation
     */
    private boolean isDeactivationWhitelisted(HttpServletRequest request) {
        String path = request.getServletPath();
        return path.startsWith("/onboarding")
                || path.equals("/workspaces/invitations/accept");
    }

    /** Marker exception used to signal a deactivated account within the lambda above. */
    private static class DeactivatedAccountException extends RuntimeException {}
}
