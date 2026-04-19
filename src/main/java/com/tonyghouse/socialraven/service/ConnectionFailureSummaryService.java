package com.tonyghouse.socialraven.service;

import com.tonyghouse.socialraven.constant.Platform;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class ConnectionFailureSummaryService {

    private static final List<String> VALIDATION_MARKERS = List.of(
            "state validation failed",
            "state mismatch",
            "missing oauth",
            "missing x oauth callback parameters",
            "authorization code is required",
            "oauth code is required",
            "recipient email is invalid",
            "client contact email is required",
            "client contact name is required",
            "invited client contact email",
            "not enabled for the handoff",
            "unsupported client handoff platform",
            "missing code",
            "missing token"
    );
    private static final List<String> RATE_LIMIT_MARKERS = List.of(
            "rate limit",
            "too many requests",
            "status code 429",
            "429"
    );
    private static final List<String> AUTH_MARKERS = List.of(
            "unauthorized",
            "forbidden",
            "access denied",
            "permission",
            "invalid token",
            "expired token",
            "token expired",
            "invalid_grant",
            "invalid_client",
            "oauthexception",
            "401",
            "403"
    );
    private static final List<String> TRANSIENT_MARKERS = List.of(
            "timeout",
            "timed out",
            "connection reset",
            "connection refused",
            "broken pipe",
            "temporarily unavailable",
            "service unavailable",
            "bad gateway",
            "gateway timeout",
            "i/o error",
            "i/o exception",
            "eofexception",
            "no route to host",
            "network is unreachable",
            "resourceaccessexception",
            "sslhandshakeexception",
            "502",
            "503",
            "504"
    );
    private static final List<String> PERSISTENCE_MARKERS = List.of(
            "jdbc",
            "hibernate",
            "sql",
            "constraint",
            "repository",
            "redis",
            "jedis",
            "datasource",
            "transaction",
            "could not commit",
            "could not execute statement"
    );

    public ConnectionFailureSummary summarize(Platform platform, String flowType, Throwable error) {
        Throwable rootCause = resolveRootCause(error);
        FailureType failureType = classify(buildHaystack(error));
        String platformLabel = formatPlatform(platform);
        String flowLabel = defaultIfBlank(flowType, "connect-accounts flow");
        String rootCauseDescription = describeThrowable(rootCause);

        String automatedSummary = switch (failureType) {
            case VALIDATION -> platformLabel + " connection failed during the " + flowLabel
                    + " because callback data or state validation failed. Review the redirect params, cookies, and request payload before retrying.";
            case RATE_LIMIT -> platformLabel + " connection failed during the " + flowLabel
                    + " because the provider appears to have rate-limited the request. Wait for the cooldown window, then retry.";
            case AUTHORIZATION -> platformLabel + " connection failed during the " + flowLabel
                    + " because the authorization or permission state looks invalid. Re-authorize the account and verify app permissions/configuration.";
            case TRANSIENT -> platformLabel + " connection failed during the " + flowLabel
                    + " because the provider/network error looks transient. A retry is likely to work once the upstream issue clears.";
            case PERSISTENCE -> platformLabel + " connection failed during the " + flowLabel
                    + " while SocialRaven was saving or reading connection state. Check internal database, Redis, and service health before retrying.";
            case UNKNOWN -> platformLabel + " connection failed during the " + flowLabel
                    + " with an unexpected application error. Manual investigation is required before asking the user to retry.";
        };

        return new ConnectionFailureSummary(
                failureType.label,
                automatedSummary,
                rootCauseDescription
        );
    }

    private FailureType classify(String haystack) {
        if (containsAny(haystack, VALIDATION_MARKERS)) {
            return FailureType.VALIDATION;
        }
        if (containsAny(haystack, RATE_LIMIT_MARKERS)) {
            return FailureType.RATE_LIMIT;
        }
        if (containsAny(haystack, AUTH_MARKERS)) {
            return FailureType.AUTHORIZATION;
        }
        if (containsAny(haystack, TRANSIENT_MARKERS)) {
            return FailureType.TRANSIENT;
        }
        if (containsAny(haystack, PERSISTENCE_MARKERS)) {
            return FailureType.PERSISTENCE;
        }
        return FailureType.UNKNOWN;
    }

    private boolean containsAny(String haystack, List<String> markers) {
        for (String marker : markers) {
            if (haystack.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    private String buildHaystack(Throwable error) {
        StringBuilder builder = new StringBuilder();
        Throwable current = error;
        int depth = 0;
        while (current != null && depth < 10) {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(current.getClass().getName().toLowerCase(Locale.ENGLISH));
            String message = current.getMessage();
            if (message != null && !message.isBlank()) {
                builder.append(' ').append(message.toLowerCase(Locale.ENGLISH));
            }
            current = current.getCause();
            depth++;
        }
        return builder.toString();
    }

    private Throwable resolveRootCause(Throwable error) {
        Throwable current = error;
        Throwable last = error;
        int depth = 0;
        while (current != null && depth < 10) {
            last = current;
            current = current.getCause();
            depth++;
        }
        return last != null ? last : error;
    }

    private String describeThrowable(Throwable throwable) {
        if (throwable == null) {
            return "Root cause unavailable";
        }

        String message = throwable.getMessage();
        if (message == null || message.isBlank()) {
            return throwable.getClass().getSimpleName();
        }
        return throwable.getClass().getSimpleName() + ": " + normalizeWhitespace(message);
    }

    private String normalizeWhitespace(String value) {
        return value.trim().replaceAll("\\s+", " ");
    }

    private String formatPlatform(Platform platform) {
        if (platform == null) {
            return "Unknown platform";
        }
        return switch (platform) {
            case x -> "X";
            case linkedin -> "LinkedIn";
            case youtube -> "YouTube";
            case instagram -> "Instagram";
            case facebook -> "Facebook";
            case tiktok -> "TikTok";
            case threads -> "Threads";
        };
    }

    private String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    public record ConnectionFailureSummary(
            String failureClassification,
            String automatedSummary,
            String rootCause
    ) {
    }

    private enum FailureType {
        VALIDATION("Invalid callback/request data"),
        RATE_LIMIT("Provider rate limit"),
        AUTHORIZATION("Auth or permission issue"),
        TRANSIENT("Transient provider/network issue"),
        PERSISTENCE("Internal persistence issue"),
        UNKNOWN("Unexpected application failure");

        private final String label;

        FailureType(String label) {
            this.label = label;
        }
    }
}
