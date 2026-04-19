package com.tonyghouse.socialraven.service;

import com.resend.Resend;
import com.tonyghouse.socialraven.constant.Platform;
import com.tonyghouse.socialraven.constant.WorkspaceRole;
import com.tonyghouse.socialraven.model.ConnectionFailureAlert;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmailServiceTest {

    @Test
    void sendInvitationEmail_rendersThymeleafTemplateAndSendsHtml() throws Exception {
        EmailService emailService = new EmailService();
        Resend resendClient = mock(Resend.class);
        ZohoMailService zohoMailService = mock(ZohoMailService.class);
        when(zohoMailService.sendWithRetry(any(), any(), any(), any(), any())).thenReturn(true);

        ReflectionTestUtils.setField(emailService, "resendClient", resendClient);
        ReflectionTestUtils.setField(emailService, "zohoMailService", zohoMailService);
        ReflectionTestUtils.setField(emailService, "templateEngine", createTemplateEngine());
        ReflectionTestUtils.setField(emailService, "resendFromAddress", "support@mail.socialraven.io");
        ReflectionTestUtils.setField(emailService, "replyToAddress", "team@socialraven.io");
        ReflectionTestUtils.setField(emailService, "appBaseUrl", "https://app.socialraven.io");

        emailService.sendInvitationEmail(
                "teammate@example.com",
                java.util.List.of("Growth Team", "Brand Studio"),
                "Alex Morgan",
                WorkspaceRole.ADMIN,
                UUID.fromString("11111111-1111-1111-1111-111111111111")
        );

        ArgumentCaptor<String> subjectCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> htmlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);

        verify(zohoMailService).sendWithRetry(
                eq("teammate@example.com"),
                subjectCaptor.capture(),
                htmlCaptor.capture(),
                textCaptor.capture(),
                eq("workspace invitation")
        );

        assertThat(subjectCaptor.getValue()).isEqualTo("Alex Morgan invited you to SocialRaven");
        assertThat(htmlCaptor.getValue()).contains("You&#39;ve been invited to collaborate in SocialRaven");
        assertThat(htmlCaptor.getValue()).contains("Growth Team");
        assertThat(htmlCaptor.getValue()).contains("Brand Studio");
        assertThat(htmlCaptor.getValue()).contains("Accept invitation");
        assertThat(htmlCaptor.getValue()).contains("https://app.socialraven.io/invite?token=11111111-1111-1111-1111-111111111111");
        assertThat(textCaptor.getValue()).contains("Alex Morgan invited you to join Growth Team, Brand Studio on SocialRaven as Admin.");
    }

    @Test
    void sendRoleChangedEmail_rendersNotificationTemplate() throws Exception {
        EmailService emailService = new EmailService();
        Resend resendClient = mock(Resend.class);
        ZohoMailService zohoMailService = mock(ZohoMailService.class);
        when(zohoMailService.sendWithRetry(any(), any(), any(), any(), any())).thenReturn(true);

        ReflectionTestUtils.setField(emailService, "resendClient", resendClient);
        ReflectionTestUtils.setField(emailService, "zohoMailService", zohoMailService);
        ReflectionTestUtils.setField(emailService, "templateEngine", createTemplateEngine());
        ReflectionTestUtils.setField(emailService, "resendFromAddress", "support@mail.socialraven.io");
        ReflectionTestUtils.setField(emailService, "replyToAddress", "team@socialraven.io");
        ReflectionTestUtils.setField(emailService, "appBaseUrl", "https://app.socialraven.io");

        emailService.sendRoleChangedEmail(
                "teammate@example.com",
                "Revenue Ops",
                "Jamie Lee",
                WorkspaceRole.EDITOR,
                WorkspaceRole.ADMIN
        );

        ArgumentCaptor<String> subjectCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> htmlCaptor = ArgumentCaptor.forClass(String.class);

        verify(zohoMailService).sendWithRetry(
                eq("teammate@example.com"),
                subjectCaptor.capture(),
                htmlCaptor.capture(),
                any(),
                eq("role change")
        );

        assertThat(subjectCaptor.getValue()).isEqualTo("Your SocialRaven workspace role has changed");
        assertThat(htmlCaptor.getValue()).contains("Your role has changed");
        assertThat(htmlCaptor.getValue()).contains("Revenue Ops");
        assertThat(htmlCaptor.getValue()).contains("Jamie Lee");
        assertThat(htmlCaptor.getValue()).contains("Previous role");
        assertThat(htmlCaptor.getValue()).contains("New role");
        assertThat(htmlCaptor.getValue()).contains("Open SocialRaven");
    }

    @Test
    void sendConnectionFailureAlertEmail_usesInternalRoutingAndIncludesStackTrace() {
        EmailService emailService = new EmailService();
        Resend resendClient = mock(Resend.class);
        ZohoMailService zohoMailService = mock(ZohoMailService.class);
        when(zohoMailService.sendWithRetry(any(), any(), any(), any(), any())).thenReturn(true);

        ReflectionTestUtils.setField(emailService, "resendClient", resendClient);
        ReflectionTestUtils.setField(emailService, "zohoMailService", zohoMailService);
        ReflectionTestUtils.setField(emailService, "templateEngine", createTemplateEngine());
        ReflectionTestUtils.setField(emailService, "resendFromAddress", "support@mail.socialraven.io");
        ReflectionTestUtils.setField(emailService, "replyToAddress", "team@socialraven.io");
        ReflectionTestUtils.setField(emailService, "appBaseUrl", "https://app.socialraven.io");
        ReflectionTestUtils.setField(emailService, "recoveryAdminEmail", "socialravenapp@gmail.com");

        emailService.sendConnectionFailureAlertEmail(new ConnectionFailureAlert(
                Platform.linkedin,
                "Workspace member connect-accounts",
                OffsetDateTime.parse("2026-04-19T10:15:30Z"),
                "workspace-123",
                "Acme Growth",
                "SocialRaven Agency",
                "Acme Client",
                "session-789",
                "user_123",
                "Alex Morgan",
                "alex@example.com",
                "client@example.com",
                "Transient provider/network issue",
                "LinkedIn connection failed because the provider call timed out. A retry is likely to succeed.",
                "LinkedIn token exchange failed",
                "SocketTimeoutException: Read timed out",
                "java.net.SocketTimeoutException: Read timed out\n\tat example.Callback.handle(Callback.java:42)"
        ));

        ArgumentCaptor<String> htmlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);

        verify(zohoMailService).sendWithRetry(
                eq("socialravenapp@gmail.com"),
                eq("Internal alert: LinkedIn connection failure - Acme Growth"),
                htmlCaptor.capture(),
                textCaptor.capture(),
                eq("connection failure alert")
        );

        assertThat(htmlCaptor.getValue()).contains("Automated triage summary");
        assertThat(htmlCaptor.getValue()).contains("LinkedIn connection failed because the provider call timed out.");
        assertThat(htmlCaptor.getValue()).contains("SocketTimeoutException: Read timed out");
        assertThat(textCaptor.getValue()).contains("Stack trace:");
        assertThat(textCaptor.getValue()).contains("Callback.java:42");
    }

    private SpringTemplateEngine createTemplateEngine() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode("HTML");
        resolver.setCharacterEncoding("UTF-8");
        resolver.setCacheable(false);

        SpringTemplateEngine templateEngine = new SpringTemplateEngine();
        templateEngine.setTemplateResolver(resolver);
        return templateEngine;
    }
}
