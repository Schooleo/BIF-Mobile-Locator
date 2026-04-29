package com.bif.server.features.auth.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.core.env.Environment;

import jakarta.annotation.PostConstruct;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class EmailService {
    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    private static final String BREVO_API_URL = "https://api.brevo.com/v3/smtp/email";
    private static final int RESPONSE_SUMMARY_MAX_LENGTH = 160;

    private final String apiKey;
    private final String senderEmail;
    private final RestTemplate restTemplate;
    private final boolean localOrDevProfile;

    public EmailService(
            @Qualifier("emailRestTemplate") RestTemplate restTemplate,
            @Value("${brevo.api.key:}") String apiKey,
            @Value("${brevo.sender.email:noreply@bifapp.com}") String senderEmail,
            Environment environment) {
        this.apiKey = apiKey;
        this.senderEmail = senderEmail;
        this.restTemplate = restTemplate;
        this.localOrDevProfile = isLocalOrDevProfile(environment);
    }

    @PostConstruct
    void validateConfigurationOnStartup() {
        if (!localOrDevProfile) {
            ensureApiKeyConfigured();
        }
    }

    @Async("emailTaskExecutor")
    public void sendOtpEmail(String toEmail, String otp) {
        String textContent = String.format("Your OTP is %s. This OTP will expire in 5 minutes.", otp);
        String htmlContent = String.format(
                "<h2>Your OTP Code</h2>" +
                "<p>Your OTP is: <b>%s</b></p>" +
                "<p>This code expires in 5 minutes.</p>", otp);
        sendEmailWithTemplate(
                toEmail,
                "Reset Password OTP",
                textContent,
                htmlContent,
                "OTP email sent");
    }

    @Async("emailTaskExecutor")
    public void sendRegisterOtpEmail(String toEmail, String otp) {
        String textContent = String.format("Your registration OTP is %s. This OTP will expire in 5 minutes.", otp);
        String htmlContent = String.format(
                "<h2>Your Registration OTP</h2>" +
                "<p>Your OTP is: <b>%s</b></p>" +
                "<p>This code expires in 5 minutes.</p>", otp);
        sendEmailWithTemplate(
                toEmail,
                "Registration OTP",
                textContent,
                htmlContent,
                "Registration OTP email sent");
    }

    private void sendEmailWithTemplate(String toEmail,
                                       String subject,
                                       String textContent,
                                       String htmlContent,
                                       String successLogMessage) {
        String maskedToEmail = maskEmail(toEmail);
        ensureApiKeyConfigured();

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("api-key", apiKey);

            Map<String, Object> body = new HashMap<>();

            // Sender
            body.put("sender", Map.of("name", "BIF Mobile Locator", "email", senderEmail));

            // To
            body.put("to", List.of(Map.of("email", toEmail)));

            // Subject
            body.put("subject", subject);

            // Content
            body.put("textContent", textContent);
            body.put("htmlContent", htmlContent);

            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(BREVO_API_URL, requestEntity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("{} (recipient={}, status={})", successLogMessage, maskedToEmail, response.getStatusCode());
            } else {
                String responseSummary = summarizeResponseBody(response.getBody());
                log.error("Failed to send email (recipient={}, status={}, reason={})",
                        maskedToEmail, response.getStatusCode(), responseSummary);
                if (log.isDebugEnabled()) {
                    log.debug("Brevo failure response body (recipient={}): {}", maskedToEmail, response.getBody());
                }
            }

        } catch (Exception e) {
            log.error("Exception occurred while sending email (recipient={})", maskedToEmail, e);
        }
    }

    private void ensureApiKeyConfigured() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("brevo.api.key is not configured");
        }
    }

    private boolean isLocalOrDevProfile(Environment environment) {
        if (environment == null) {
            return false;
        }
        for (String profile : environment.getActiveProfiles()) {
            if ("local".equalsIgnoreCase(profile)
                    || "dev".equalsIgnoreCase(profile)
                    || "test".equalsIgnoreCase(profile)) {
                return true;
            }
        }
        return false;
    }

    private String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return "unknown";
        }
        int atIndex = email.indexOf('@');
        if (atIndex <= 0 || atIndex == email.length() - 1) {
            return "***";
        }
        String localPart = email.substring(0, atIndex);
        String domain = email.substring(atIndex);
        if (localPart.length() < 3) {
            return "***" + domain;
        }
        return localPart.charAt(0) + "***" + domain;
    }

    private String summarizeResponseBody(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return "empty response body";
        }
        String normalized = responseBody.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= RESPONSE_SUMMARY_MAX_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, RESPONSE_SUMMARY_MAX_LENGTH) + "...";
    }
}
