package com.ghouse.socialraven.config;

import com.clerk.backend_api.helpers.security.models.SessionAuthObjectV2;
import com.ghouse.socialraven.model.ClerkAuthenticationToken;
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
            }
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        filterChain.doFilter(request, response);
    }
}
