package com.tonyghouse.socialraven.service;

import com.resend.Resend;
import com.resend.services.emails.Emails;
import com.resend.services.emails.model.CreateEmailOptions;
import com.tonyghouse.socialraven.constant.WorkspaceRole;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmailServiceTest {

    @Test
    void sendInvitationEmail_rendersThymeleafTemplateAndSendsHtml() throws Exception {
        EmailService emailService = new EmailService();
        Resend resendClient = mock(Resend.class);
        Emails emails = mock(Emails.class);
        when(resendClient.emails()).thenReturn(emails);
        when(emails.send(any(CreateEmailOptions.class))).thenReturn(null);

        ReflectionTestUtils.setField(emailService, "resendClient", resendClient);
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

        ArgumentCaptor<CreateEmailOptions> optionsCaptor = ArgumentCaptor.forClass(CreateEmailOptions.class);
        verify(emails).send(optionsCaptor.capture());

        CreateEmailOptions options = optionsCaptor.getValue();
        assertThat(options.getSubject()).isEqualTo("Alex Morgan invited you to SocialRaven");
        assertThat(options.getHtml()).contains("You've been invited to collaborate in SocialRaven");
        assertThat(options.getHtml()).contains("Growth Team");
        assertThat(options.getHtml()).contains("Brand Studio");
        assertThat(options.getHtml()).contains("Accept invitation");
        assertThat(options.getHtml()).contains("https://app.socialraven.io/invite?token=11111111-1111-1111-1111-111111111111");
        assertThat(options.getText()).contains("Alex Morgan invited you to join Growth Team, Brand Studio on SocialRaven as Admin.");
    }

    @Test
    void sendRoleChangedEmail_rendersNotificationTemplate() throws Exception {
        EmailService emailService = new EmailService();
        Resend resendClient = mock(Resend.class);
        Emails emails = mock(Emails.class);
        when(resendClient.emails()).thenReturn(emails);
        when(emails.send(any(CreateEmailOptions.class))).thenReturn(null);

        ReflectionTestUtils.setField(emailService, "resendClient", resendClient);
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

        ArgumentCaptor<CreateEmailOptions> optionsCaptor = ArgumentCaptor.forClass(CreateEmailOptions.class);
        verify(emails).send(optionsCaptor.capture());

        CreateEmailOptions options = optionsCaptor.getValue();
        assertThat(options.getSubject()).isEqualTo("Your SocialRaven workspace role has changed");
        assertThat(options.getHtml()).contains("Your role has changed");
        assertThat(options.getHtml()).contains("Revenue Ops");
        assertThat(options.getHtml()).contains("Jamie Lee");
        assertThat(options.getHtml()).contains("Previous role");
        assertThat(options.getHtml()).contains("New role");
        assertThat(options.getHtml()).contains("Open SocialRaven");
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
