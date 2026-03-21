package com.ghouse.socialraven.service;

import com.ghouse.socialraven.constant.WorkspaceRole;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.*;

import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class EmailService {

    @Autowired
    private SesClient sesClient;

    @Value("${aws.ses.from}")
    private String fromAddress;

    @Value("${socialraven.app.base-url}")
    private String appBaseUrl;

    /**
     * Sends a workspace invitation email.
     *
     * @param toEmail       recipient email
     * @param workspaceNames workspace name(s) the user is invited to (one or more)
     * @param inviterName   display name of the inviter (or their user ID if no name)
     * @param role          role being assigned
     * @param token         unique invite token
     */
    public void sendInvitationEmail(String toEmail,
                                    List<String> workspaceNames,
                                    String inviterName,
                                    WorkspaceRole role,
                                    UUID token) {
        String inviteLink = appBaseUrl + "/invite?token=" + token;
        String workspaceList = String.join(", ", workspaceNames);
        String roleName = role.name().charAt(0) + role.name().substring(1).toLowerCase();
        boolean isNewUser = true; // email copy — we don't know if they exist; use generic copy

        String subject = inviterName + " invited you to " + workspaceList + " on SocialRaven";

        String htmlBody = buildInviteHtml(toEmail, workspaceNames, inviterName, roleName, inviteLink);
        String textBody = buildInviteText(workspaceList, inviterName, roleName, inviteLink);

        try {
            sesClient.sendEmail(SendEmailRequest.builder()
                    .source(fromAddress)
                    .destination(Destination.builder().toAddresses(toEmail).build())
                    .message(Message.builder()
                            .subject(Content.builder().data(subject).charset("UTF-8").build())
                            .body(Body.builder()
                                    .html(Content.builder().data(htmlBody).charset("UTF-8").build())
                                    .text(Content.builder().data(textBody).charset("UTF-8").build())
                                    .build())
                            .build())
                    .build());

            log.info("Invitation email sent to={}, workspaces={}, token={}", toEmail, workspaceList, token);
        } catch (Exception e) {
            log.error("Failed to send invitation email to={}: {}", toEmail, e.getMessage(), e);
            throw new RuntimeException("Failed to send invitation email", e);
        }
    }

    private String buildInviteHtml(String toEmail,
                                   List<String> workspaceNames,
                                   String inviterName,
                                   String roleName,
                                   String inviteLink) {
        StringBuilder workspaceBullets = new StringBuilder();
        for (String ws : workspaceNames) {
            workspaceBullets.append("<li style=\"margin-bottom:4px;\">").append(escapeHtml(ws)).append("</li>");
        }

        return "<!DOCTYPE html>" +
                "<html><head><meta charset=\"UTF-8\"></head><body style=\"font-family:sans-serif;color:#111;max-width:480px;margin:0 auto;padding:32px 16px;\">" +
                "<img src=\"https://socialraven.io/SocialRavenLogo.svg\" alt=\"SocialRaven\" style=\"height:40px;margin-bottom:24px;\">" +
                "<h2 style=\"margin:0 0 16px;\">You've been invited</h2>" +
                "<p style=\"margin:0 0 12px;\"><strong>" + escapeHtml(inviterName) + "</strong> has invited you to join the following workspace(s) on SocialRaven as <strong>" + escapeHtml(roleName) + "</strong>:</p>" +
                "<ul style=\"margin:0 0 24px;padding-left:20px;\">" + workspaceBullets + "</ul>" +
                "<a href=\"" + escapeHtml(inviteLink) + "\" style=\"display:inline-block;background:#000;color:#fff;text-decoration:none;padding:12px 24px;border-radius:8px;font-weight:600;\">Accept invitation</a>" +
                "<p style=\"margin:24px 0 0;font-size:12px;color:#666;\">This invitation expires in 7 days. If you don't have an account yet, you'll be prompted to create one.</p>" +
                "<p style=\"margin:8px 0 0;font-size:12px;color:#999;\">Or copy this link: <a href=\"" + escapeHtml(inviteLink) + "\" style=\"color:#999;\">" + escapeHtml(inviteLink) + "</a></p>" +
                "</body></html>";
    }

    private String buildInviteText(String workspaceList,
                                   String inviterName,
                                   String roleName,
                                   String inviteLink) {
        return inviterName + " has invited you to join " + workspaceList + " on SocialRaven as " + roleName + ".\n\n" +
                "Accept the invitation:\n" + inviteLink + "\n\n" +
                "This invitation expires in 7 days.";
    }

    private String escapeHtml(String s) {
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
