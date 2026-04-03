package com.tonyghouse.socialraven.service.reporting;

import com.tonyghouse.socialraven.exception.SocialRavenException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ClientReportTokenService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    @Value("${socialraven.client-report.secret}")
    private String clientReportSecret;

    public String generateToken(String linkId, OffsetDateTime expiresAt) {
        String payload = linkId + ":" + expiresAt.toEpochSecond();
        String encodedPayload = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return encodedPayload + "." + sign(encodedPayload);
    }

    public ValidatedClientReportToken parseAndValidate(String token) {
        if (token == null || token.isBlank()) {
            throw new SocialRavenException("Client report token is required", HttpStatus.BAD_REQUEST);
        }

        String[] parts = token.split("\\.");
        if (parts.length != 2) {
            throw new SocialRavenException("Client report link is invalid", HttpStatus.NOT_FOUND);
        }

        String encodedPayload = parts[0];
        String expectedSignature = sign(encodedPayload);
        byte[] provided = parts[1].getBytes(StandardCharsets.UTF_8);
        byte[] expected = expectedSignature.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(provided, expected)) {
            throw new SocialRavenException("Client report link is invalid", HttpStatus.NOT_FOUND);
        }

        try {
            String payload = new String(Base64.getUrlDecoder().decode(encodedPayload), StandardCharsets.UTF_8);
            int delimiter = payload.lastIndexOf(':');
            if (delimiter <= 0 || delimiter >= payload.length() - 1) {
                throw new SocialRavenException("Client report link is invalid", HttpStatus.NOT_FOUND);
            }

            String linkId = payload.substring(0, delimiter);
            long expiresAtEpochSecond = Long.parseLong(payload.substring(delimiter + 1));
            OffsetDateTime expiresAt = OffsetDateTime.ofInstant(
                    Instant.ofEpochSecond(expiresAtEpochSecond),
                    ZoneOffset.UTC
            );
            return new ValidatedClientReportToken(linkId, expiresAt);
        } catch (IllegalArgumentException ex) {
            throw new SocialRavenException("Client report link is invalid", HttpStatus.NOT_FOUND);
        }
    }

    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(clientReportSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            byte[] raw = mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        } catch (Exception ex) {
            throw new SocialRavenException("Failed to sign client report link", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public record ValidatedClientReportToken(String linkId, OffsetDateTime expiresAt) {
    }
}
