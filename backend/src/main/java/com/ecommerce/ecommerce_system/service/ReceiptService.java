package com.ecommerce.ecommerce_system.service;

import com.ecommerce.ecommerce_system.model.*;
import com.ecommerce.ecommerce_system.repository.PaymentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Builds the digital receipt for a payment. Kept separate from PaymentController
 * so the same receipt shape can back the API response, the on-screen printable
 * view and the emailed copy.
 */
@Service
public class ReceiptService {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a");

    @Autowired
    private PaymentRepository paymentRepository;

    /**
     * @param receiptNumber stable public identifier, e.g. RCP-2026-000042
     */
    public Map<String, Object> build(Payment payment, String receiptNumber) {
        Order order = payment.getOrder();

        List<Map<String, Object>> lines = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        if (order != null && order.getOrderItems() != null) {
            for (OrderItem item : order.getOrderItems()) {
                // Fall back to unitPrice x qty for lines placed before lineTotal existed.
                BigDecimal line = item.getLineTotal() != null
                        ? item.getLineTotal()
                        : (item.getUnitPrice() == null ? BigDecimal.ZERO
                                : item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity() == null ? 0 : item.getQuantity())));

                Product product = item.getProduct();
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("name", product != null ? product.getName() : "Item #" + item.getId());
                row.put("quantity", item.getQuantity());
                row.put("unitPrice", item.getUnitPrice());
                row.put("lineTotal", line);
                lines.add(row);

                subtotal = subtotal.add(line);
            }
        }

        BigDecimal discount = order != null && order.getDiscountAmount() != null
                ? order.getDiscountAmount()
                : BigDecimal.ZERO;

        BigDecimal total = payment.getAmount() != null ? payment.getAmount()
                : (order != null ? order.getTotalAmount() : BigDecimal.ZERO);

        Map<String, Object> customer = new LinkedHashMap<>();
        Customer c = order != null ? order.getCustomer() : null;
        customer.put("name", c != null ? c.getName() : null);
        customer.put("email", c != null ? c.getEmail() : null);
        customer.put("phone", c != null ? c.getPhone() : null);

        Map<String, Object> receipt = new LinkedHashMap<>();
        receipt.put("receiptNumber", receiptNumber);
        receipt.put("orderId", order != null ? order.getId() : null);
        receipt.put("issuedAt", STAMP.format(payment.getTransactionDate() == null ? LocalDateTime.now() : payment.getTransactionDate()));
        receipt.put("customer", customer);
        receipt.put("shippingAddress", order != null ? order.getShippingAddress() : null);
        receipt.put("items", lines);
        receipt.put("subtotal", subtotal);
        receipt.put("discount", discount);
        receipt.put("couponCode", order != null ? order.getCouponCode() : null);
        receipt.put("total", total);
        receipt.put("amountPaid", payment.getPaymentStatus() == PaymentStatus.PAID ? payment.getAmount() : BigDecimal.ZERO);
        receipt.put("balanceDue", payment.getPaymentStatus() == PaymentStatus.PAID ? BigDecimal.ZERO : total);
        receipt.put("paymentMode", payment.getPaymentMode());
        receipt.put("paymentStatus", payment.getPaymentStatus());
        receipt.put("transactionRef", payment.getTransactionRef());
        receipt.put("transactionDate", payment.getTransactionDate() == null ? null : STAMP.format(payment.getTransactionDate()));
        receipt.put("orderStatus", order != null ? order.getStatus() : null);
        receipt.put("currency", "INR");
        return receipt;
    }

    /** Builds the receipt and derives a stable-looking receipt number. */
    public Map<String, Object> build(Payment payment) {
        return build(payment, receiptNumber(payment));
    }

    public String receiptNumber(Payment payment) {
        long id = payment.getId() == null ? 0L : payment.getId();
        int year = payment.getTransactionDate() == null
                ? LocalDateTime.now().getYear()
                : payment.getTransactionDate().getYear();
        return String.format("RCP-%d-%06d", year, id);
    }
}