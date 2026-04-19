package com.tonyghouse.socialraven.service;

import com.tonyghouse.socialraven.constant.Platform;
import com.tonyghouse.socialraven.entity.CompanyEntity;
import com.tonyghouse.socialraven.entity.WorkspaceClientConnectionSessionEntity;
import com.tonyghouse.socialraven.entity.WorkspaceEntity;
import com.tonyghouse.socialraven.model.ConnectionFailureAlert;
import com.tonyghouse.socialraven.repo.CompanyRepo;
import com.tonyghouse.socialraven.repo.WorkspaceRepo;
import com.tonyghouse.socialraven.util.WorkspaceContext;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class ConnectionFailureAlertService {

    private static final String WORKSPACE_MEMBER_FLOW = "Workspace member connect-accounts";
    private static final String CLIENT_HANDOFF_FLOW = "Client handoff connect-accounts";

    @Autowired
    private EmailService emailService;

    @Autowired
    private ConnectionFailureSummaryService connectionFailureSummaryService;

    @Autowired
    private WorkspaceRepo workspaceRepo;

    @Autowired
    private CompanyRepo companyRepo;

    @Autowired
    private ClerkUserService clerkUserService;

    public void notifyWorkspaceConnectionFailure(Platform platform, String userId, Exception exception) {
        try {
            WorkspaceSnapshot snapshot = loadWorkspaceSnapshot(WorkspaceContext.getWorkspaceId());
            ClerkUserService.UserProfile userProfile = loadUserProfile(userId);
            sendAlert(new ConnectionFailureAlert(
                    platform,
                    WORKSPACE_MEMBER_FLOW,
                    OffsetDateTime.now(ZoneOffset.UTC),
                    defaultIfBlank(snapshot.workspaceId(), "Unavailable"),
                    defaultIfBlank(snapshot.workspaceName(), "Unavailable"),
                    defaultIfBlank(snapshot.agencyLabel(), "Unavailable"),
                    "Not applicable",
                    "Not applicable",
                    defaultIfBlank(userId, "Unavailable"),
                    resolveDisplayName(userId, userProfile),
                    userProfile != null ? defaultIfBlank(userProfile.email(), "Unavailable") : "Unavailable",
                    "Not applicable",
                    null,
                    null,
                    summarizeMessage(exception),
                    null,
                    stackTrace(exception)
            ), exception);
        } catch (Exception alertException) {
            log.error("Failed to send workspace connection failure alert for platform={} userId={}",
                    platform, userId, alertException);
        }
    }

    public void notifyClientConnectionFailure(Platform platform,
                                              WorkspaceClientConnectionSessionEntity session,
                                              String actorDisplayName,
                                              String actorEmail,
                                              Exception exception) {
        try {
            WorkspaceSnapshot snapshot = loadWorkspaceSnapshot(session != null ? session.getWorkspaceId() : null);
            sendAlert(new ConnectionFailureAlert(
                    platform,
                    CLIENT_HANDOFF_FLOW,
                    OffsetDateTime.now(ZoneOffset.UTC),
                    defaultIfBlank(snapshot.workspaceId(), "Unavailable"),
                    defaultIfBlank(snapshot.workspaceName(), "Unavailable"),
                    defaultIfBlank(defaultIfBlank(session != null ? session.getAgencyLabel() : null, snapshot.agencyLabel()), "Unavailable"),
                    defaultIfBlank(session != null ? session.getClientLabel() : null, snapshot.workspaceName()),
                    defaultIfBlank(session != null ? session.getId() : null, "Unavailable"),
                    "Unavailable",
                    defaultIfBlank(actorDisplayName, "Unavailable"),
                    defaultIfBlank(actorEmail, "Unavailable"),
                    defaultIfBlank(session != null ? session.getRecipientEmail() : null, "Not applicable"),
                    null,
                    null,
                    summarizeMessage(exception),
                    null,
                    stackTrace(exception)
            ), exception);
        } catch (Exception alertException) {
            log.error("Failed to send client handoff connection failure alert for platform={} sessionId={}",
                    platform, session != null ? session.getId() : null, alertException);
        }
    }

    private void sendAlert(ConnectionFailureAlert baseAlert, Exception exception) {
        ConnectionFailureSummaryService.ConnectionFailureSummary summary =
                connectionFailureSummaryService.summarize(baseAlert.platform(), baseAlert.flowType(), exception);
        emailService.sendConnectionFailureAlertEmail(new ConnectionFailureAlert(
                baseAlert.platform(),
                baseAlert.flowType(),
                baseAlert.occurredAt(),
                baseAlert.workspaceId(),
                baseAlert.workspaceName(),
                baseAlert.agencyLabel(),
                baseAlert.clientLabel(),
                baseAlert.sessionId(),
                baseAlert.actorUserId(),
                baseAlert.actorName(),
                baseAlert.actorEmail(),
                baseAlert.invitedRecipientEmail(),
                summary.failureClassification(),
                summary.automatedSummary(),
                defaultIfBlank(baseAlert.errorMessage(), summarizeMessage(exception)),
                summary.rootCause(),
                baseAlert.stackTrace()
        ));
    }

    private WorkspaceSnapshot loadWorkspaceSnapshot(String workspaceId) {
        if (workspaceId == null || workspaceId.isBlank()) {
            return new WorkspaceSnapshot(null, null, null);
        }

        WorkspaceEntity workspace = workspaceRepo.findByIdAndDeletedAtIsNull(workspaceId).orElse(null);
        if (workspace == null) {
            return new WorkspaceSnapshot(workspaceId, null, null);
        }

        CompanyEntity company = workspace.getCompanyId() != null ? companyRepo.findById(workspace.getCompanyId()).orElse(null) : null;
        String agencyLabel = company != null && company.getName() != null && !company.getName().isBlank()
                ? company.getName()
                : workspace.getName();
        return new WorkspaceSnapshot(workspace.getId(), workspace.getName(), agencyLabel);
    }

    private ClerkUserService.UserProfile loadUserProfile(String userId) {
        if (userId == null || userId.isBlank()) {
            return null;
        }
        try {
            return clerkUserService.getUserProfile(userId);
        } catch (Exception exception) {
            log.warn("Failed to resolve Clerk profile for connection alert userId={}", userId, exception);
            return null;
        }
    }

    private String resolveDisplayName(String userId, ClerkUserService.UserProfile profile) {
        if (profile == null) {
            return defaultIfBlank(userId, "Unavailable");
        }

        String firstName = defaultIfBlank(profile.firstName(), "");
        String lastName = defaultIfBlank(profile.lastName(), "");
        String fullName = (firstName + " " + lastName).trim();
        if (!fullName.isBlank()) {
            return fullName;
        }
        if (profile.email() != null && !profile.email().isBlank()) {
            return profile.email();
        }
        return defaultIfBlank(userId, "Unavailable");
    }

    private String summarizeMessage(Throwable throwable) {
        if (throwable == null || throwable.getMessage() == null || throwable.getMessage().isBlank()) {
            return throwable != null ? throwable.getClass().getSimpleName() : "Unknown error";
        }
        return throwable.getMessage().trim().replaceAll("\\s+", " ");
    }

    private String stackTrace(Throwable throwable) {
        if (throwable == null) {
            return "Stack trace unavailable";
        }

        StringWriter writer = new StringWriter();
        throwable.printStackTrace(new PrintWriter(writer));
        return writer.toString().trim();
    }

    private String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private record WorkspaceSnapshot(String workspaceId,
                                     String workspaceName,
                                     String agencyLabel) {
    }
}
