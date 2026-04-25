package com.bif.server.features.auth.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class EmailService {
    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    private static final String BREVO_API_URL = "https://api.brevo.com/v3/smtp/email";

    private final String apiKey;
    private final String senderEmail;
    private final RestTemplate restTemplate;

    public EmailService(
            @Value("${brevo.api.key:}") String apiKey,
            @Value("${brevo.sender.email:noreply@bifapp.com}") String senderEmail) {
        this.apiKey = apiKey;
        this.senderEmail = senderEmail;
        this.restTemplate = new RestTemplate();
    }

    public void sendOtpEmail(String toEmail, String otp) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("brevo.api.key is not configured. Email will not be sent to {}", toEmail);
            return;
        }

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
            body.put("subject", "Reset Password OTP");

            // Content
            String textContent = String.format("Your OTP is %s. This OTP will expire in 5 minutes.", otp);
            body.put("textContent", textContent);
            
            // HTML content (optional, added for better UX)
            String htmlContent = String.format(
                    "<h2>Your OTP Code</h2>" +
                    "<p>Your OTP is: <b>%s</b></p>" +
                    "<p>This code expires in 5 minutes.</p>", otp);
            body.put("htmlContent", htmlContent);

            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(BREVO_API_URL, requestEntity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("OTP email sent to {}", toEmail);
            } else {
                log.error("Failed to send email to {}. Status: {}, Response: {}", 
                        toEmail, response.getStatusCode(), response.getBody());
            }

        } catch (Exception e) {
            log.error("Exception occurred while sending email to {}", toEmail, e);
        }
    }
}
