package com.tonyghouse.socialraven.config;

import com.clerk.backend_api.helpers.security.AuthenticateRequest;
import com.clerk.backend_api.helpers.security.models.AuthenticateRequestOptions;
import com.clerk.backend_api.helpers.security.models.RequestState;
import com.clerk.backend_api.helpers.security.models.SessionAuthObjectV2;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class ClerkAuthHelper {

    @Value("${socialraven.clerk.secret}")
    private String clerkSecretKey;

    @Value("${socialraven.clerk.jwt-key:}")
    private String clerkJwtKey;

    @Value("${socialraven.clerk.authorized-party}")
    private String authorizedParty;

    private AuthenticateRequestOptions authenticateRequestOptions;

    @PostConstruct
    public void initAuthOptions() {
        AuthenticateRequestOptions.Builder builder;
        String normalizedJwtKey = normalizeJwtKey(clerkJwtKey);

        if (normalizedJwtKey != null) {
            builder = AuthenticateRequestOptions.jwtKey(normalizedJwtKey);
            log.info("Clerk auth configured in networkless mode (jwtKey).");
        } else {
            builder = AuthenticateRequestOptions.secretKey(clerkSecretKey);
            log.warn("Clerk auth is using secretKey mode (JWKS network call). Set CLERK_JWT_PUBLIC_KEY to enable networkless verification.");
        }

        this.authenticateRequestOptions = builder
                .authorizedParties(List.of(authorizedParty))
                .build();
    }

    public SessionAuthObjectV2 authenticate(Map<String, List<String>> requestHeaders) {

        RequestState requestState = AuthenticateRequest.authenticateRequest(
                requestHeaders,
                authenticateRequestOptions
        );

        if (!requestState.isSignedIn()) {
            return null;
        }

        return (SessionAuthObjectV2) requestState.toAuth();
    }

    public boolean isSignedIn(Map<String, List<String>> requestHeaders) {
        RequestState requestState = AuthenticateRequest.authenticateRequest(requestHeaders, authenticateRequestOptions);
        if (!requestState.isSignedIn()) {
            return false;
        }

        return requestState.isSignedIn();
    }

    private String normalizeJwtKey(String jwtKey) {
        if (jwtKey == null) {
            return null;
        }

        String normalized = jwtKey.trim();
        if (normalized.isEmpty()) {
            return null;
        }

        // .env values often store PEM newlines as literal "\n"
        return normalized.replace("\\n", "\n");
    }
}
