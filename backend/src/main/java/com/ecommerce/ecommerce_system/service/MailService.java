package com.ecommerce.ecommerce_system.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    // Brevo transactional email endpoint (REST/HTTPS — no SMTP ports needed).
    private static final String BREVO_URL = "https://api.brevo.com/v3/smtp/email";

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${brevo.api.key:}")
    private String apiKey;

    @Value("${brevo.sender.email:}")
    private String senderEmail;

    @Value("${brevo.sender.name:}")
    private String senderName;

    /**
     * Emails the OTP via the Brevo API. Returns true when Brevo accepted the
     * message. Returns false when delivery is unavailable; authentication endpoints fail closed.
     */
    public boolean sendOtp(String to, String otp) {
        String html = "<div style='font-family:Arial,sans-serif;max-width:480px;margin:auto;'>"
                + "<h2 style='color:#6C2BD9;'>ShopNow</h2>"
                + "<p>Hi,</p><p>Your verification code is:</p>"
                + "<p style='font-size:28px;font-weight:bold;letter-spacing:6px;color:#6C2BD9;'>" + otp + "</p>"
                + "<p>It expires in 5 minutes. If you didn't request this, you can ignore this email.</p></div>";
        return sendHtml(to, "Your ShopNow verification code", html);
    }

    /**
     * Fire-and-forget HTML email on the mail pool.
     *
     * Used for everything customer-facing (order, payment, shipping, restock).
     * The caller has already persisted the in-app notification, so a slow or
     * failing provider must not hold up - or roll back - the business
     * operation that triggered it.
     */
    @Async("notificationExecutor")
    public void sendHtmlAsync(String to, String subject, String html) {
        sendHtml(to, subject, html);
    }

    /**
     * Sends any HTML email through Brevo. Returns true if Brevo accepted it.
     */
    public boolean sendHtml(String to, String subject, String html) {
        if (apiKey == null || apiKey.isBlank()) {
            log.info("No Brevo API key configured — skipping real email for {}", to);
            return false;
        }
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("sender", Map.of("email", senderEmail, "name", senderName));
            body.put("to", List.of(Map.of("email", to)));
            body.put("subject", subject);
            body.put("htmlContent", html);

            String json = objectMapper.writeValueAsString(body);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BREVO_URL))
                    .timeout(Duration.ofSeconds(20))
                    .header("api-key", apiKey)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.info("Email sent to {} via Brevo (status {})", to, response.statusCode());
                return true;
            }
            log.warn("Brevo rejected email to {}: status {} body {}", to, response.statusCode(), response.body());
            return false;
        } catch (Exception e) {
            log.warn("Failed to email {}: {}", to, e.getMessage());
            return false;
        }
    }
}