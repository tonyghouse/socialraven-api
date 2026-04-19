package com.tonyghouse.socialraven.service;

import com.tonyghouse.socialraven.constant.Platform;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConnectionFailureSummaryServiceTest {

    private final ConnectionFailureSummaryService service = new ConnectionFailureSummaryService();

    @Test
    void summarize_classifiesTransientTimeouts() {
        RuntimeException exception = new RuntimeException(
                "LinkedIn token exchange failed",
                new java.net.SocketTimeoutException("Read timed out")
        );

        ConnectionFailureSummaryService.ConnectionFailureSummary summary =
                service.summarize(Platform.linkedin, "Workspace member connect-accounts", exception);

        assertThat(summary.failureClassification()).isEqualTo("Transient provider/network issue");
        assertThat(summary.automatedSummary()).contains("retry is likely to work");
        assertThat(summary.rootCause()).contains("SocketTimeoutException");
    }

    @Test
    void summarize_classifiesRateLimits() {
        RuntimeException exception = new RuntimeException("Provider returned 429 Too Many Requests");

        ConnectionFailureSummaryService.ConnectionFailureSummary summary =
                service.summarize(Platform.tiktok, "Client handoff connect-accounts", exception);

        assertThat(summary.failureClassification()).isEqualTo("Provider rate limit");
        assertThat(summary.automatedSummary()).contains("cooldown window");
    }

    @Test
    void summarize_classifiesAuthorizationProblems() {
        RuntimeException exception = new RuntimeException("Instagram returned 401 Unauthorized");

        ConnectionFailureSummaryService.ConnectionFailureSummary summary =
                service.summarize(Platform.instagram, "Workspace member connect-accounts", exception);

        assertThat(summary.failureClassification()).isEqualTo("Auth or permission issue");
        assertThat(summary.automatedSummary()).contains("Re-authorize the account");
    }
}
