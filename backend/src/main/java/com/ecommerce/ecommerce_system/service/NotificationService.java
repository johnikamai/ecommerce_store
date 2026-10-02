package com.ecommerce.ecommerce_system.service;

import com.ecommerce.ecommerce_system.model.Customer;
import com.ecommerce.ecommerce_system.model.Notification;
import com.ecommerce.ecommerce_system.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * Creates in-app notifications and, whenever Brevo is configured, also emails
 * the customer a friendly HTML summary. Types: ORDER, PAYMENT, SHIPPING,
 * DELIVERY, RESTOCK, LOW_STOCK, OFFER.
 *
 * The in-app row is always written first and inline; the email is dispatched on
 * the background pool so a slow provider can never delay the order, payment or
 * return that triggered the notification.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private static final String BRAND = "ShopEase";

    @Autowired
    private NotificationRepository notificationRepository;

    /**
     * Transactional notification types. SMS is reserved for these because they
     * are time-critical and per-order; promotional ones (RESTOCK, LOW_STOCK,
     * OFFER) stay email/in-app only so customers are not spammed and the SMS
     * bill stays predictable.
     */
    private static final Set<String> SMS_TYPES = Set.of("ORDER", "PAYMENT", "SHIPPING", "DELIVERY");

    @Autowired
    private MailService mailService;

    @Autowired
    private SmsService smsService;

    /** Where stock alerts are emailed. Optional - blank disables admin email. */
    @Value("${admin.alert.email:}")
    private String adminEmail;

    @Value("${app.admin.orders-url:http://localhost:5173/admin/orders}")
    private String adminOrdersUrl;

    /** Simple HTML shell reused by every transactional email. */
    private String shell(String heading, String body) {
        return "<div style='font-family:Arial,sans-serif;max-width:520px;margin:auto;border:1px solid #eee;border-radius:12px;overflow:hidden;'>"
                + "<div style='background:#7C6AE8;padding:16px 24px;'>"
                + "<span style='color:#fff;font-weight:bold;font-size:18px;'>" + BRAND + "</span></div>"
                + "<div style='padding:24px;'>"
                + "<h2 style='margin:0 0 12px;color:#222;'>" + heading + "</h2>"
                + "<p style='color:#444;line-height:1.5;'>" + body + "</p>"
                + "</div></div>";
    }

    /**
     * Saves an in-app notification, then emails (and for transactional events
     * texts) the customer in the background.
     *
     * The in-app row is the source of truth and is already stored, so neither
     * channel can delay or fail the order/payment/return that triggered this.
     */
    public void notify(Customer customer, String type, String message, String subject, String html) {
        notify(customer, type, message, subject, html, message);
    }

    /** Same as {@link #notify} but with an explicit, shorter SMS body. */
    public void notify(Customer customer, String type, String message, String subject, String html, String smsText) {
        if (customer == null) {
            return;
        }
        Notification notification = new Notification();
        notification.setCustomer(customer);
        notification.setType(type);
        notification.setMessage(message);
        notificationRepository.save(notification);

        // The in-app record is the source of truth and is already stored, so the
        // email goes out on the background pool: a Brevo hiccup can no longer
        // delay or fail the order/payment/return that triggered this.
        String email = customer.getEmail();
        if (email != null && !email.isBlank()) {
            mailService.sendHtmlAsync(email, subject, shell(subject, html));
        }

        if (SMS_TYPES.contains(type)) {
            String phone = customer.getPhone();
            if (phone != null && !phone.isBlank()) {
                smsService.sendSmsAsync(phone, trimSms(smsText));
            }
        }
    }

    /** Twilio caps a single message at 1600 characters; keep well under it. */
    private static String trimSms(String text) {
        if (text == null) {
            return null;
        }
        return text.length() <= 320 ? text : text.substring(0, 317) + "...";
    }

    public void orderPlaced(Customer customer, Long orderId, String total) {
        notify(customer, "ORDER",
                "Your order #" + orderId + " was placed successfully — total ₹" + total + ".",
                "Order " + orderId + " confirmed",
                "Thanks for shopping with " + BRAND + "! Your order #" + orderId + " was placed and is being prepared.<br/><br/>Order total: <b>₹" + total + "</b><br/><br/>We'll email you again when it ships.",
                BRAND + ": Order #" + orderId + " confirmed. Total ₹" + total + ". We'll notify you when it ships.");
    }

    public void paymentMade(Customer customer, Long orderId, String method, String status) {
        boolean paid = status.equalsIgnoreCase("PAID");
        notify(customer, "PAYMENT",
                (paid ? "Payment received" : "Payment pending") + " for order #" + orderId + " (via " + method + ").",
                paid ? "Payment received for order " + orderId : "Payment pending for order " + orderId,
                "Method: <b>" + method + "</b><br/>Status: <b>" + status + "</b> for order <b>#" + orderId + "</b>.",
                BRAND + ": " + (paid ? "Payment received" : "Payment pending")
                        + " for order #" + orderId + " via " + method + ".");
    }

    public void shipped(Customer customer, Long orderId) {
        notify(customer, "SHIPPING",
                "Good news — order #" + orderId + " has been shipped and is on its way!",
                "Order " + orderId + " shipped",
                "Your order <b>#" + orderId + "</b> is on the move. Expect delivery soon.",
                BRAND + ": Order #" + orderId + " has shipped and is on its way.");
    }

    public void delivered(Customer customer, Long orderId) {
        notify(customer, "DELIVERY",
                "Order #" + orderId + " has been delivered. Enjoy! You can return items within the return window if needed.",
                "Order " + orderId + " delivered",
                "Your order <b>#" + orderId + "</b> has been delivered. Happy shopping with " + BRAND + "!",
                BRAND + ": Order #" + orderId + " delivered. Thanks for shopping with us!");
    }

    public void orderCancelled(Customer customer, Long orderId) {
        notify(customer, "ORDER",
                "Order #" + orderId + " was cancelled. Any stock was released back.",
                "Order " + orderId + " cancelled",
                "Order <b>#" + orderId + "</b> was cancelled. If you paid online, the refund appears on your next statement.",
                BRAND + ": Order #" + orderId + " was cancelled.");
    }

    public void refunded(Customer customer, Long returnId, String productName) {
        notify(customer, "PAYMENT",
                "Refund for '" + productName + "' (return #" + returnId + ") has been processed.",
                "Refund processed",
                "Your refund for <b>" + productName + "</b> (return <b>#" + returnId + "</b>) has been processed.",
                BRAND + ": Refund processed for your return #" + returnId + ".");
    }

    public void refundProcessed(Customer customer, Long orderId, String amount) {
        notify(customer, "PAYMENT",
                "Your payment of ₹" + amount + " for order #" + orderId + " has been refunded (order cancelled).",
                "Refund issued for order " + orderId,
                "Your payment of <b>₹" + amount + "</b> for order <b>#" + orderId + "</b> was refunded because the order was cancelled.",
                BRAND + ": ₹" + amount + " for order #" + orderId + " has been refunded.");
    }

    public void offer(Customer customer, String code, String description) {
        String message = "New offer: " + code + " — " + description;
        notify(customer, "OFFER", message, "New offer: " + code,
                "Use code <b style='font-size:18px;color:#7C6AE8;'>" + code + "</b> at checkout.<br/>" + description + ".");
    }

    public void restocked(Customer customer, String productName) {
        notify(customer, "RESTOCK",
                "Good news! '" + productName + "' is back in stock. Get it before it sells out!",
                "Back in stock: " + productName,
                "<b>" + productName + "</b> is back in stock on " + BRAND + ".<br/><br/>Hurry — quantities are limited and it may sell out again.");
    }

    /**
     * Announces a new offer to every customer off the request thread.
     *
     * Runs on the mail pool so creating a coupon returns immediately instead of
     * blocking once per recipient. Each call needs its own transaction, because
     * the caller has already committed by the time this runs.
     */
    @Async("notificationExecutor")
    @Transactional
    public void broadcastOffer(String code, String description, List<Customer> customers) {
        for (Customer customer : customers) {
            try {
                offer(customer, code, description);
            } catch (Exception e) {
                log.warn("Offer notification to customer {} failed: {}", customer.getId(), e.getMessage());
            }
        }
        log.info("Offer {} announced to {} customers", code, customers.size());
    }

    /**
     * Emails the store's admin address about a stock alert. Optional: skipped
     * silently when no admin address is configured.
     */
    @Async("notificationExecutor")
    public void alertAdminLowStock(String productName, String type, int stock, int reorderLevel) {
        String to = adminEmail;
        if (to == null || to.isBlank()) {
            return;
        }
        String heading = "Stock alert: " + productName;
        String html = "<p style='color:#444;line-height:1.5;'>"
                + "<b>" + productName + "</b> triggered a <b>" + type + "</b> alert.<br/><br/>"
                + "Stock now: <b>" + stock + "</b> · reorder level: <b>" + reorderLevel + "</b><br/><br/>"
                + "<a href='" + adminOrdersUrl + "' style='display:inline-block;background:#7C6AE8;color:#fff;padding:10px 18px;border-radius:8px;text-decoration:none;'>Open admin console</a>"
                + "</p>";
        mailService.sendHtml(to, heading, shell(heading, html));
    }
}