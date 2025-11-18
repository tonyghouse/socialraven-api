package com.ghouse.socialraven.util;

import com.clerk.backend_api.helpers.security.models.SessionAuthObjectV2;
import org.springframework.security.core.context.SecurityContext;

public class SecurityContextUtil {

    private SecurityContextUtil() {
    }

    public static String getUserId(SecurityContext context) {
        SessionAuthObjectV2 auth = (SessionAuthObjectV2) context.getAuthentication().getPrincipal();
        return auth.getSub();
    }
}
