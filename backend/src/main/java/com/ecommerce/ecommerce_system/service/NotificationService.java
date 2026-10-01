package com.ecommerce.ecommerce_system.service;

import com.ecommerce.ecommerce_system.model.Customer;
import com.ecommerce.ecommerce_system.model.Notification;
import com.ecommerce.ecommerce_system.repository.NotificationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Creates in-app notifications and, whenever Brevo is configured, also emails
 * the customer a friendly HTML summary. Types: ORDER, PAYMENT, SHIPPING,
 * DELIVERY, RESTOCK, OFFER.
 */
@Service
public class NotificationService {

    private static final String BRAND = "ShopEase";

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private MailService mailService;

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

    /** Saves an in-app notification and tries to email the customer as well. */
    public void notify(Customer customer, String type, String message, String subject, String html) {
        if (customer == null) {
            return;
        }
        Notification notification = new Notification();
        notification.setCustomer(customer);
        notification.setType(type);
        notification.setMessage(message);
        notificationRepository.save(notification);

        String email = customer.getEmail();
        if (email != null && !email.isBlank()) {
            mailService.sendHtml(email, subject, shell(subject, html));
        }
    }

    public void orderPlaced(Customer customer, Long orderId, String total) {
        notify(customer, "ORDER",
                "Your order #" + orderId + " was placed successfully — total ₹" + total + ".",
                "Order " + orderId + " confirmed",
                "Thanks for shopping with " + BRAND + "! Your order #" + orderId + " was placed and is being prepared.<br/><br/>Order total: <b>₹" + total + "</b><br/><br/>We'll email you again when it ships.");
    }

    public void paymentMade(Customer customer, Long orderId, String method, String status) {
        notify(customer, "PAYMENT",
                (status.equalsIgnoreCase("PAID") ? "Payment received" : "Payment pending") + " for order #" + orderId + " (via " + method + ").",
                status.equalsIgnoreCase("PAID") ? "Payment received for order " + orderId : "Payment pending for order " + orderId,
                "Method: <b>" + method + "</b><br/>Status: <b>" + status + "</b> for order <b>#" + orderId + "</b>.");
    }

    public void shipped(Customer customer, Long orderId) {
        notify(customer, "SHIPPING",
                "Good news — order #" + orderId + " has been shipped and is on its way!",
                "Order " + orderId + " shipped",
                "Your order <b>#" + orderId + "</b> is on the move. Expect delivery soon.");
    }

    public void delivered(Customer customer, Long orderId) {
        notify(customer, "DELIVERY",
                "Order #" + orderId + " has been delivered. Enjoy! You can return items within the return window if needed.",
                "Order " + orderId + " delivered",
                "Your order <b>#" + orderId + "</b> has been delivered. Happy shopping with " + BRAND + "!");
    }

    public void orderCancelled(Customer customer, Long orderId) {
        notify(customer, "ORDER",
                "Order #" + orderId + " was cancelled. Any stock was released back.",
                "Order " + orderId + " cancelled",
                "Order <b>#" + orderId + "</b> was cancelled. If you paid online, the refund appears on your next statement.");
    }

    public void refunded(Customer customer, Long returnId, String productName) {
        notify(customer, "PAYMENT",
                "Refund for '" + productName + "' (return #" + returnId + ") has been processed.",
                "Refund processed",
                "Your refund for <b>" + productName + "</b> (return <b>#" + returnId + "</b>) has been processed.");
    }

    public void refundProcessed(Customer customer, Long orderId, String amount) {
        notify(customer, "PAYMENT",
                "Your payment of ₹" + amount + " for order #" + orderId + " has been refunded (order cancelled).",
                "Refund issued for order " + orderId,
                "Your payment of <b>₹" + amount + "</b> for order <b>#" + orderId + "</b> was refunded because the order was cancelled.");
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
}