package com.tonyghouse.socialraven.service;

import com.tonyghouse.socialraven.constant.WorkspaceRole;
import com.resend.Resend;
import com.resend.services.emails.model.CreateEmailOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class EmailService {

    @Autowired
    private Resend resendClient;

    @Value("${resend.from}")
    private String fromAddress;

    @Value("${socialraven.app.base-url}")
    private String appBaseUrl;

    public void sendInvitationEmail(String toEmail,
                                    List<String> workspaceNames,
                                    String inviterName,
                                    WorkspaceRole role,
                                    UUID token) {
        String inviteLink = appBaseUrl + "/invite?token=" + token;
        String workspaceList = String.join(", ", workspaceNames);
        String roleName = role.name().charAt(0) + role.name().substring(1).toLowerCase();

        String subject = inviterName + " invited you to " + workspaceList + " on SocialRaven";
        String htmlBody = buildInviteHtml(toEmail, workspaceNames, inviterName, roleName, inviteLink);
        String textBody = buildInviteText(workspaceList, inviterName, roleName, inviteLink);

        try {
            CreateEmailOptions params = CreateEmailOptions.builder()
                    .from(fromAddress)
                    .to(toEmail)
                    .subject(subject)
                    .html(htmlBody)
                    .text(textBody)
                    .build();

            resendClient.emails().send(params);

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
        StringBuilder workspaceItems = new StringBuilder();
        for (String ws : workspaceNames) {
            workspaceItems.append(
                "<tr><td style=\"padding:10px 16px;border-bottom:1px solid #f0f0f0;font-size:14px;color:#374151;\">")
                .append(escapeHtml(ws))
                .append("</td></tr>");
        }

        String workspaceSection = workspaceNames.size() == 1
                ? "<p style=\"margin:0 0 6px;font-size:13px;font-weight:600;color:#6b7280;letter-spacing:.05em;text-transform:uppercase;\">Workspace</p>" +
                  "<p style=\"margin:0 0 28px;font-size:16px;font-weight:600;color:#111827;\">" + escapeHtml(workspaceNames.get(0)) + "</p>"
                : "<p style=\"margin:0 0 6px;font-size:13px;font-weight:600;color:#6b7280;letter-spacing:.05em;text-transform:uppercase;\">Workspaces</p>" +
                  "<table style=\"width:100%;border-collapse:collapse;margin-bottom:28px;border:1px solid #e5e7eb;border-radius:8px;overflow:hidden;\">" +
                  workspaceItems + "</table>";

        return "<!DOCTYPE html>" +
            "<html lang=\"en\"><head><meta charset=\"UTF-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"></head>" +
            "<body style=\"margin:0;padding:0;background:#f9fafb;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;\">" +
            "<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:#f9fafb;padding:40px 16px;\">" +
            "<tr><td align=\"center\">" +
            "<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"max-width:520px;\">" +

            // Header
            "<tr><td style=\"padding-bottom:24px;\">" +
            "<img src=\"https://socialraven.io/SocialRavenLogo.svg\" alt=\"SocialRaven\" style=\"height:36px;\">" +
            "</td></tr>" +

            // Card
            "<tr><td style=\"background:#ffffff;border-radius:12px;border:1px solid #e5e7eb;padding:40px 36px;\">" +

            // Title
            "<h1 style=\"margin:0 0 8px;font-size:22px;font-weight:700;color:#111827;\">You've been invited</h1>" +
            "<p style=\"margin:0 0 28px;font-size:15px;color:#6b7280;\">You've been invited to collaborate on SocialRaven.</p>" +

            // Inviter row
            "<p style=\"margin:0 0 6px;font-size:13px;font-weight:600;color:#6b7280;letter-spacing:.05em;text-transform:uppercase;\">Invited by</p>" +
            "<p style=\"margin:0 0 28px;font-size:15px;color:#111827;\">" + escapeHtml(inviterName) + "</p>" +

            // Workspace(s)
            workspaceSection +

            // Role
            "<p style=\"margin:0 0 6px;font-size:13px;font-weight:600;color:#6b7280;letter-spacing:.05em;text-transform:uppercase;\">Your role</p>" +
            "<p style=\"margin:0 0 32px;font-size:15px;color:#111827;\">" + escapeHtml(roleName) + "</p>" +

            // CTA button
            "<a href=\"" + escapeHtml(inviteLink) + "\" style=\"display:inline-block;background:#111827;color:#ffffff;text-decoration:none;padding:13px 28px;border-radius:8px;font-size:15px;font-weight:600;\">Accept invitation</a>" +

            // Footer note inside card
            "<p style=\"margin:24px 0 0;font-size:13px;color:#9ca3af;\">This invitation expires in 7&nbsp;days. If you don't have an account yet, you'll be asked to create one.</p>" +

            "</td></tr>" +

            // Bottom link
            "<tr><td style=\"padding:20px 0 0;\">" +
            "<p style=\"margin:0;font-size:12px;color:#9ca3af;word-break:break-all;\">Or paste this link in your browser: <a href=\"" + escapeHtml(inviteLink) + "\" style=\"color:#6b7280;\">" + escapeHtml(inviteLink) + "</a></p>" +
            "</td></tr>" +

            "</table>" +
            "</td></tr></table>" +
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
