package com.tonyghouse.socialraven.service;

import com.resend.Resend;
import com.resend.services.emails.model.CreateEmailOptions;
import com.tonyghouse.socialraven.constant.WorkspaceRole;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class EmailService {

    private static final String INVITATION_TEMPLATE = "email/workspace-invitation";
    private static final String NOTIFICATION_TEMPLATE = "email/workspace-notification";

    @Autowired
    private Resend resendClient;

    @Autowired
    private TemplateEngine templateEngine;

    @Value("${resend.from}")
    private String fromAddress;

    @Value("${socialraven.app.base-url}")
    private String appBaseUrl;

    @Value("${socialraven.recovery.admin-email:socialravenapp@gmail.com}")
    private String recoveryAdminEmail;

    public void sendInvitationEmail(String toEmail,
                                    List<String> workspaceNames,
                                    String inviterName,
                                    WorkspaceRole role,
                                    UUID token) {
        String inviteLink = appBaseUrl + "/invite?token=" + token;
        String workspaceList = String.join(", ", workspaceNames);
        String roleName = formatRole(role);
        String subject = inviterName + " invited you to SocialRaven";

        Context context = baseContext(
                "Join your SocialRaven workspace invitation.",
                "Workspace invitation",
                "You've been invited to collaborate in SocialRaven",
                "Review the invitation details below and accept when you're ready."
        );
        context.setVariable("inviterName", inviterName);
        context.setVariable("roleName", roleName);
        context.setVariable("inviteLink", inviteLink);
        context.setVariable("workspaceNames", workspaceNames);
        context.setVariable("singleWorkspace", workspaceNames.size() == 1);
        context.setVariable("workspaceLabel", workspaceNames.size() == 1 ? "Workspace" : "Workspaces");
        context.setVariable("footerMessage", "This invitation expires in 7 days. If you do not have an account yet, you will be prompted to create one.");

        String htmlBody = templateEngine.process(INVITATION_TEMPLATE, context);
        String textBody = inviterName + " invited you to join " + workspaceList + " on SocialRaven as " + roleName + ".\n\n"
                + "Accept invitation: " + inviteLink + "\n\n"
                + "This invitation expires in 7 days.";

        sendEmail(toEmail, subject, htmlBody, textBody, "workspace invitation");
    }

    public void sendRoleChangedEmail(String toEmail,
                                     String workspaceName,
                                     String changedByName,
                                     WorkspaceRole previousRole,
                                     WorkspaceRole newRole) {
        Context context = notificationContext(
                "Your SocialRaven workspace role was updated.",
                "Permissions update",
                "Your role has changed",
                "A teammate updated your permissions for this workspace.",
                List.of(
                        detail("Workspace", workspaceName),
                        detail("Changed by", changedByName),
                        detail("Previous role", formatRole(previousRole)),
                        detail("New role", formatRole(newRole))
                ),
                "Review access",
                "Open SocialRaven",
                appBaseUrl,
                "If this change was unexpected, review the workspace membership settings in SocialRaven."
        );

        String textBody = "Your role for " + workspaceName + " on SocialRaven has changed.\n\n"
                + "Changed by: " + changedByName + "\n"
                + "Previous role: " + formatRole(previousRole) + "\n"
                + "New role: " + formatRole(newRole) + "\n\n"
                + "Open SocialRaven: " + appBaseUrl;

        sendNotificationEmail(toEmail, "Your SocialRaven workspace role has changed", context, textBody, "role change");
    }

    public void sendMemberRemovedEmail(String toEmail,
                                       String workspaceName,
                                       String removedByName) {
        Context context = notificationContext(
                "Your workspace access in SocialRaven was removed.",
                "Access update",
                "Your access has been removed",
                "You no longer have access to this workspace.",
                List.of(
                        detail("Workspace", workspaceName),
                        detail("Removed by", removedByName)
                ),
                "Need context?",
                "Open SocialRaven",
                appBaseUrl,
                "If you still need access, contact a workspace admin or owner."
        );

        String textBody = "Your access to " + workspaceName + " on SocialRaven has been removed.\n\n"
                + "Removed by: " + removedByName + "\n\n"
                + "Open SocialRaven: " + appBaseUrl;

        sendNotificationEmail(toEmail, "Your SocialRaven workspace access has been removed", context, textBody, "member removal");
    }

    public void sendInvitationRevokedEmail(String toEmail,
                                           String workspaceName,
                                           String revokedByName) {
        Context context = notificationContext(
                "A pending SocialRaven invitation was canceled.",
                "Invitation update",
                "Your invitation was canceled",
                "This invitation is no longer valid and the link can no longer be used.",
                List.of(
                        detail("Workspace", workspaceName),
                        detail("Canceled by", revokedByName)
                ),
                "Invite status",
                null,
                null,
                "If you still need access, ask a workspace admin or owner to send a new invitation."
        );

        String textBody = "Your invitation to " + workspaceName + " on SocialRaven has been canceled.\n\n"
                + "Canceled by: " + revokedByName + "\n"
                + "This invite link can no longer be used.";

        sendNotificationEmail(toEmail, "Your SocialRaven invitation has been canceled", context, textBody, "invitation revocation");
    }

    public void sendWorkspaceDeletedEmail(String toEmail,
                                          String workspaceName,
                                          String deletedByName) {
        Context context = notificationContext(
                "A SocialRaven workspace was moved to deleted workspaces.",
                "Workspace update",
                "Workspace deleted",
                "This workspace has been moved to deleted workspaces and is no longer active.",
                List.of(
                        detail("Workspace", workspaceName),
                        detail("Deleted by", deletedByName)
                ),
                "Workspace status",
                "Review workspaces",
                appBaseUrl,
                "Workspace owners can restore it later if needed."
        );

        String textBody = "Workspace deleted: " + workspaceName + " on SocialRaven.\n\n"
                + "Deleted by: " + deletedByName + "\n\n"
                + "Open SocialRaven: " + appBaseUrl;

        sendNotificationEmail(toEmail, "A SocialRaven workspace was deleted", context, textBody, "workspace deletion");
    }

    public void sendWorkspaceRestoredEmail(String toEmail,
                                           String workspaceName,
                                           String restoredByName) {
        Context context = notificationContext(
                "A SocialRaven workspace was restored.",
                "Workspace update",
                "Workspace restored",
                "This workspace is active again and available for normal use.",
                List.of(
                        detail("Workspace", workspaceName),
                        detail("Restored by", restoredByName)
                ),
                "Workspace status",
                "Open SocialRaven",
                appBaseUrl,
                "You can return to the workspace to resume collaboration."
        );

        String textBody = "Workspace restored: " + workspaceName + " on SocialRaven.\n\n"
                + "Restored by: " + restoredByName + "\n\n"
                + "Open SocialRaven: " + appBaseUrl;

        sendNotificationEmail(toEmail, "A SocialRaven workspace was restored", context, textBody, "workspace restore");
    }

    public void sendFailedCollectionRecoveryEmail(String toEmail,
                                                  Long collectionId,
                                                  String description,
                                                  String failureReasonSummary,
                                                  int attemptNumber) {
        String recoveryLink = appBaseUrl + "/recovery-drafts/" + collectionId;
        Context context = notificationContext(
                "A SocialRaven post collection needs attention.",
                "Publishing issue",
                "Your post collection needs attention",
                "We couldn't publish this collection to one or more selected channels. Create a recovery draft to fix the content or media and reschedule it.",
                List.of(
                        detail("Collection ID", String.valueOf(collectionId)),
                        detail("Reminder attempt", String.valueOf(attemptNumber)),
                        detail("Issue summary", failureReasonSummary),
                        detail("Content preview", summarizeDescription(description))
                ),
                "Next step",
                "Open Recovery Draft",
                recoveryLink,
                "Once you create a recovery draft, reminder emails for this collection will stop."
        );

        String textBody = "Your SocialRaven post collection needs attention.\n\n"
                + "Collection ID: " + collectionId + "\n"
                + "Reminder attempt: " + attemptNumber + "\n"
                + "Issue summary: " + failureReasonSummary + "\n"
                + "Content preview: " + summarizeDescription(description) + "\n\n"
                + "Open Recovery Draft: " + recoveryLink;

        sendNotificationEmail(toEmail, "Your SocialRaven post collection needs attention", context, textBody, "failed collection recovery");
    }

    public void sendFailedCollectionEscalationEmail(Long collectionId,
                                                    String workspaceId,
                                                    String ownerUserId,
                                                    String ownerEmail,
                                                    String description,
                                                    int attemptCount) {
        String recoveryLink = appBaseUrl + "/recovery-drafts/" + collectionId;
        Context context = notificationContext(
                "A failed SocialRaven post collection needs manual intervention.",
                "Recovery escalation",
                "A failed post collection needs manual intervention",
                "The creator has not created a recovery draft after repeated reminders. Manual follow-up is now required to help complete the remaining channels.",
                List.of(
                        detail("Collection ID", String.valueOf(collectionId)),
                        detail("Workspace ID", workspaceId),
                        detail("Owner user ID", ownerUserId),
                        detail("Owner email", ownerEmail != null && !ownerEmail.isBlank() ? ownerEmail : "Unavailable"),
                        detail("Reminder attempts", String.valueOf(attemptCount)),
                        detail("Content preview", summarizeDescription(description))
                ),
                "Intervention",
                "Open Recovery Draft",
                recoveryLink,
                "Follow up with the workspace owner and create or guide the recovery draft manually if needed."
        );

        String textBody = "A failed SocialRaven post collection needs manual intervention.\n\n"
                + "Collection ID: " + collectionId + "\n"
                + "Workspace ID: " + workspaceId + "\n"
                + "Owner user ID: " + ownerUserId + "\n"
                + "Owner email: " + (ownerEmail != null && !ownerEmail.isBlank() ? ownerEmail : "Unavailable") + "\n"
                + "Reminder attempts: " + attemptCount + "\n"
                + "Content preview: " + summarizeDescription(description) + "\n\n"
                + "Open Recovery Draft: " + recoveryLink;

        sendNotificationEmail(recoveryAdminEmail, "SocialRaven recovery escalation required", context, textBody, "failed collection escalation");
    }

    private void sendNotificationEmail(String toEmail,
                                       String subject,
                                       Context context,
                                       String textBody,
                                       String emailType) {
        String htmlBody = templateEngine.process(NOTIFICATION_TEMPLATE, context);
        sendEmail(toEmail, subject, htmlBody, textBody, emailType);
    }

    private void sendEmail(String toEmail,
                           String subject,
                           String htmlBody,
                           String textBody,
                           String emailType) {
        try {
            CreateEmailOptions params = CreateEmailOptions.builder()
                    .from(fromAddress)
                    .to(toEmail)
                    .subject(subject)
                    .html(htmlBody)
                    .text(textBody)
                    .build();

            resendClient.emails().send(params);
            log.info("{} email sent to={}", emailType, toEmail);
        } catch (Exception e) {
            log.error("Failed to send {} email to={}: {}", emailType, toEmail, e.getMessage(), e);
            throw new RuntimeException("Failed to send " + emailType + " email", e);
        }
    }

    private Context notificationContext(String preheader,
                                        String eyebrow,
                                        String title,
                                        String intro,
                                        List<Map<String, String>> details,
                                        String panelLabel,
                                        String actionLabel,
                                        String actionUrl,
                                        String footerMessage) {
        Context context = baseContext(preheader, eyebrow, title, intro);
        context.setVariable("details", details);
        context.setVariable("panelLabel", panelLabel);
        context.setVariable("actionLabel", actionLabel);
        context.setVariable("actionUrl", actionUrl);
        context.setVariable("footerMessage", footerMessage);
        return context;
    }

    private Context baseContext(String preheader,
                                String eyebrow,
                                String title,
                                String intro) {
        Context context = new Context(Locale.ENGLISH);
        context.setVariable("preheader", preheader);
        context.setVariable("eyebrow", eyebrow);
        context.setVariable("title", title);
        context.setVariable("intro", intro);
        context.setVariable("productName", "SocialRaven");
        context.setVariable("supportUrl", appBaseUrl);
        return context;
    }

    private Map<String, String> detail(String label, String value) {
        return Map.of("label", label, "value", value);
    }

    private String formatRole(WorkspaceRole role) {
        String normalized = role.name().toLowerCase(Locale.ENGLISH).replace('_', ' ');
        return Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
    }

    private String summarizeDescription(String description) {
        if (description == null || description.isBlank()) {
            return "No description provided";
        }
        String normalized = description.trim().replaceAll("\\s+", " ");
        return normalized.length() > 120 ? normalized.substring(0, 117) + "..." : normalized;
    }
}
