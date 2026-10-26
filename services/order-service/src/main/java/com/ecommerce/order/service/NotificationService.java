package com.ecommerce.order.service;
import com.ecommerce.order.model.Customer;
import com.ecommerce.common.outbox.OutboxPublisher;
import org.springframework.stereotype.Service;
import java.util.*;
@Service
public class NotificationService {
    private static final String BRAND="ShopEase";
    private final OutboxPublisher outbox;
    public NotificationService(OutboxPublisher outbox){this.outbox=outbox;}
    public void notify(Customer customer,String type,String message,String subject,String html){notify(customer,type,message,subject,html,message);}
    public void notify(Customer customer,String type,String message,String subject,String html,String sms){
        if(customer==null)return;var payload=new LinkedHashMap<String,Object>();payload.put("customerId",customer.getId());payload.put("type",type);payload.put("message",message);payload.put("subject",subject);payload.put("html",html);payload.put("sms",sms);
        outbox.publish(UUID.randomUUID().toString(),"http://notification-service/internal/events","NOTIFY",payload);
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
}
