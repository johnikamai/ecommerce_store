package com.ecommerce.ecommerce_system.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Delivers OTP emails through the Brevo (aka Sendinblue) REST API over HTTPS.
 *
 * <p>SMTP (Gmail app passwords etc.) is deliberately no longer used because
 * outbound SMTP is blocked by some ISPs, whereas HTTPS always works. To enable
 * real delivery, put your Brevo API key and a verified sender email in
 * mail-credentials.properties in the project root:
 *
 * <pre>
 *   brevo.api.key=xkeysib-xxxxxxxxxxxx
 *   brevo.sender.email=you@example.com
 *   brevo.sender.name=ShopNow
 * </pre>
 *
 * If no key is configured, the code returns false and the on-screen demo flow
 * is used instead.
 */
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);
    private static final String BREVO_URL = "https://api.brevo.com/v3/smtp/email";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);

    @Value("${brevo.api.key:}")
    private String apiKey;

    @Value("${brevo.sender.email:}")
    private String senderEmail;

    @Value("${brevo.sender.name:}")
    private String senderName;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(REQUEST_TIMEOUT)
            .build();

    /**
     * Tries to email the OTP to the given address through Brevo. Returns true
     * if the message was accepted by the API. When no API key (or no verified
     * sender) is configured this falls back to the on-screen demo behaviour by
     * returning false.
     */
    public boolean sendOtp(String to, String otp) {
        String html =
                "<div style='font-family:Arial,sans-serif;max-width:480px;margin:auto;'>"
                        + "<h2 style='color:#6C2BD9;'>ShopNow</h2>"
                        + "<p>Hi,</p>"
                        + "<p>Your verification code is:</p>"
                        + "<p style='font-size:28px;font-weight:bold;letter-spacing:6px;color:#6C2BD9;'>" + otp + "</p>"
                        + "<p>It expires in 5 minutes. If you didn't request this, you can ignore this email.</p>"
                        + "</div>";
        return sendHtml(to, "Your ShopNow verification code", html);
    }

    /**
     * Sends an arbitrary HTML email through Brevo. Returns true when the API
     * accepted it, false when delivery is not configured (API key / sender) or
     * the call failed. The transaction emails (order, payment, shipping) reuse
     * this so they get real delivery whenever Brevo is configured.
     */
    public boolean sendHtml(String to, String subject, String htmlContent) {
        if (apiKey == null || apiKey.isBlank()) {
            log.info("No Brevo API key configured - skipping real email for {}", to);
            return false;
        }
        if (senderEmail == null || senderEmail.isBlank()) {
            log.warn("brevo.sender.email not configured - skipping real email for {}", to);
            return false;
        }

        try {
            Map<String, Object> sender = new HashMap<>();
            sender.put("email", senderEmail);
            if (senderName != null && !senderName.isBlank()) {
                sender.put("name", senderName);
            }

            Map<String, Object> recipient = new HashMap<>();
            recipient.put("email", to);

            Map<String, Object> body = new HashMap<>();
            body.put("sender", sender);
            body.put("to", List.of(recipient));
            body.put("subject", subject);
            body.put("htmlContent", htmlContent);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BREVO_URL))
                    .timeout(REQUEST_TIMEOUT)
                    .header("api-key", apiKey)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.info("Email '{}' sent to {} via Brevo ({})", subject, to, response.body());
                return true;
            }
            log.warn("Brevo API returned {} for {}: {}", response.statusCode(), to, response.body());
            return false;
        } catch (Exception e) {
            log.warn("Failed to email {} to {}: {}", subject, to, e.getMessage());
            return false;
        }
    }
}