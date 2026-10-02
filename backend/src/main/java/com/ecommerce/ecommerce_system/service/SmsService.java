package com.ecommerce.ecommerce_system.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

/**
 * Transactional SMS through the Twilio REST API (HTTPS — no extra SDK).
 *
 * Stays dormant unless TWILIO_ACCOUNT_SID / TWILIO_AUTH_TOKEN / TWILIO_FROM_NUMBER
 * are set, so the store runs normally without SMS configured. Phone numbers are
 * normalised to E.164 before sending.
 */
@Service
public class SmsService {

    private static final Logger log = LoggerFactory.getLogger(SmsService.class);

    private static final String TWILIO_URL =
            "https://api.twilio.com/2010-04-01/Accounts/%s/Messages.json";

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Value("${twilio.account.sid:}")
    private String accountSid;

    @Value("${twilio.auth.token:}")
    private String authToken;

    @Value("${twilio.from.number:}")
    private String fromNumber;

    /** Country code applied to bare 10-digit numbers. */
    @Value("${sms.default.country.code:91}")
    private String defaultCountryCode;

    /** Explicit on/off switch; when unset SMS is enabled iff credentials exist. */
    @Value("${sms.enabled:}")
    private Boolean enabled;

    public boolean isConfigured() {
        boolean hasCreds = accountSid != null && !accountSid.isBlank()
                && authToken != null && !authToken.isBlank()
                && fromNumber != null && !fromNumber.isBlank();
        if (enabled != null) {
            return enabled && hasCreds;
        }
        return hasCreds;
    }

    /**
     * Fire-and-forget SMS on the notification pool, mirroring the email path so
     * the order/payment that triggered it is never blocked on the carrier.
     */
    @Async("notificationExecutor")
    public void sendSmsAsync(String to, String body) {
        sendSms(to, body);
    }

    /** Returns true when Twilio accepted the message. */
    public boolean sendSms(String to, String body) {
        if (!isConfigured()) {
            log.info("SMS not configured — skipping SMS for {}", maskPhone(to));
            return false;
        }
        String e164 = toE164(to);
        if (e164 == null) {
            log.warn("Unusable phone number '{}' — SMS skipped", maskPhone(to));
            return false;
        }
        try {
            String form = "To=" + enc(e164)
                    + "&From=" + enc(fromNumber)
                    + "&Body=" + enc(body);

            String credentials = Base64.getEncoder()
                    .encodeToString((accountSid + ":" + authToken).getBytes(StandardCharsets.UTF_8));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(String.format(TWILIO_URL, accountSid)))
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Basic " + credentials)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.info("SMS sent to {} via Twilio", maskPhone(e164));
                return true;
            }
            log.warn("Twilio rejected SMS to {}: status {} body {}",
                    maskPhone(e164), response.statusCode(), response.body());
            return false;
        } catch (Exception e) {
            log.warn("Failed to SMS {}: {}", maskPhone(e164), e.getMessage());
            return false;
        }
    }

    /**
     * Normalises a stored phone number to E.164.
     * Returns null when the number cannot be made valid.
     */
    public String toE164(String raw) {
        if (raw == null) {
            return null;
        }
        String digits = raw.replaceAll("\\D", "");
        if (digits.startsWith("00")) {
            digits = digits.substring(2);
        }
        if (digits.length() == 10) {
            digits = defaultCountryCode + digits;
        } else if (digits.length() < 10 || digits.length() > 15) {
            return null;
        }
        return "+" + digits;
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /** Keeps the last two digits only, so logs never hold a full phone number. */
    private static String maskPhone(String phone) {
        if (phone == null || phone.length() < 3) {
            return "***";
        }
        return "***" + phone.substring(phone.length() - 2);
    }
}