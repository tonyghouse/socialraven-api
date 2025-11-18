package com.ghouse.socialraven.config;

import com.clerk.backend_api.helpers.security.AuthenticateRequest;
import com.clerk.backend_api.helpers.security.models.AuthenticateRequestOptions;
import com.clerk.backend_api.helpers.security.models.RequestState;
import com.clerk.backend_api.helpers.security.models.SessionAuthObjectV2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class ClerkAuthHelper {

    @Value("${socialraven.clerk.secret}")
    private String clerkSecretKey;


    public SessionAuthObjectV2 authenticate(Map<String, List<String>> requestHeaders) {

        RequestState requestState = AuthenticateRequest.authenticateRequest(
                requestHeaders,
                AuthenticateRequestOptions
                        .secretKey(clerkSecretKey)
                        .build()
        );

        if (!requestState.isSignedIn()) {
            return null;
        }

        return (SessionAuthObjectV2) requestState.toAuth();
    }

    public boolean isSignedIn(Map<String, List<String>> requestHeaders) {
        RequestState requestState = AuthenticateRequest.authenticateRequest(requestHeaders, AuthenticateRequestOptions
                .secretKey(clerkSecretKey)
                .build());
        if (!requestState.isSignedIn()) {
            return false;
        }

        SessionAuthObjectV2 auth = (SessionAuthObjectV2) requestState.toAuth();
        System.out.println("UserId: " + auth.getSub());
        return requestState.isSignedIn();
    }
}