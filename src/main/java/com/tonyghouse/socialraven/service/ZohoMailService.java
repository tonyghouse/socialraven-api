package com.tonyghouse.socialraven.service;

import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class ZohoMailService {

    private static final int MAX_RETRIES = 3;
    private static final long RETRY_BACKOFF_MS = 60 * 1000L; // 1 minute

    @Autowired
    private JavaMailSender zohoMailSender;

    @Value("${zoho.smtp.from}")
    private String fromAddress;

    /**
     * Synchronous send with up to 3 retries and 3-minute backoff between attempts.
     *
     * @return true if the email was sent successfully, false if all attempts failed
     */
    public boolean sendWithRetry(String toEmail,
                                  String subject,
                                  String htmlBody,
                                  String textBody,
                                  String emailType) {
        int attempt = 0;
        while (attempt < MAX_RETRIES) {
            attempt++;
            try {
                MimeMessage message = zohoMailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
                helper.setFrom(fromAddress);
                helper.setTo(toEmail);
                helper.setSubject(subject);
                helper.setText(textBody, htmlBody);
                zohoMailSender.send(message);
                log.info("Zoho: {} email sent to={} on attempt {}", emailType, toEmail, attempt);
                return true;
            } catch (Exception e) {
                log.warn("Zoho: attempt {}/{} failed for {} email to={}: {}",
                        attempt, MAX_RETRIES, emailType, toEmail, e.getMessage());
                if (attempt < MAX_RETRIES) {
                    try {
                        Thread.sleep(RETRY_BACKOFF_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.error("Zoho retry interrupted for {} email to={}", emailType, toEmail);
                        return false;
                    }
                }
            }
        }
        log.error("Zoho: all {} attempts exhausted for {} email to={}", MAX_RETRIES, emailType, toEmail);
        return false;
    }

}
