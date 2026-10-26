package com.ecommerce.payment.controller;

import com.ecommerce.payment.model.*;
import com.ecommerce.payment.repo.PaymentRepository;
import com.ecommerce.payment.security.CustomerGuard;
import com.ecommerce.payment.service.ReceiptService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Payment records, status tracking and digital receipts.
 *
 * GET /api/payments                          list (admin)
 * GET /api/payments/customer/{customerId}    own payments (customer or admin)
 * GET /api/payments/order/{orderId}          payment for an order
 * POST /api/payments                         record a payment (admin)
 * PUT  /api/payments/{id}/status             move status, guarded by ALLOWED_TRANSITIONS
 * GET  /api/payments/{id}/receipt            digital receipt
 */
@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentRepository paymentRepository;


    private final ReceiptService receiptService;
    private final CustomerGuard customerGuard;

    public PaymentController(
                             PaymentRepository paymentRepository,


                             ReceiptService receiptService,
                             CustomerGuard customerGuard) {

        this.paymentRepository = paymentRepository;


        this.receiptService = receiptService;
        this.customerGuard = customerGuard;
    }

    // GET /api/payments  ->  admin, flattened for the payments table
    @GetMapping
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getAll() {
        return summarise(paymentRepository.findAll());
    }

    // GET /api/payments/customer/{customerId}
    @GetMapping("/customer/{customerId}")
    @Transactional(readOnly = true)
    public ResponseEntity<?> getByCustomer(@PathVariable Long customerId, Authentication auth) {
        if (!customerGuard.canAccess(customerId, auth)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Access denied");
        }
        return ResponseEntity.ok(summarise(paymentRepository.findByCustomerId(customerId)));
    }

    // GET /api/payments/order/{orderId}
    @GetMapping("/order/{orderId}")
    @Transactional(readOnly = true)
    public ResponseEntity<?> getByOrder(@PathVariable Long orderId, Authentication auth) {
        return paymentRepository.findByOrderId(orderId)
                .map(p -> {
                    Long customerId = p.getOrder() != null && p.getOrder().getCustomer() != null
                            ? p.getOrder().getCustomer().getId() : null;
                    if (customerId != null && !customerGuard.canAccess(customerId, auth)) {
                        return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Access denied");
                    }
                    return ResponseEntity.ok(summarise(List.of(p)).get(0));
                })
                .orElse(ResponseEntity.notFound().build());
    }



    // PUT /api/payments/{id}/status  { "status": "PAID", "transactionRef": "UPI123" }


    // GET /api/payments/{id}/receipt
    @GetMapping("/{id}/receipt")
    @Transactional(readOnly = true)
    public ResponseEntity<?> receipt(@PathVariable Long id, Authentication auth) {
        return paymentRepository.findById(id)
                .map(p -> {
                    Long customerId = p.getOrder() != null && p.getOrder().getCustomer() != null
                            ? p.getOrder().getCustomer().getId() : null;
                    if (customerId != null && !customerGuard.canAccess(customerId, auth)) {
                        return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Access denied");
                    }
                    return ResponseEntity.ok(receiptService.build(p));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    private void audit(Payment payment, String who) {
        payment.setStatusUpdatedAt(LocalDateTime.now());
        payment.setStatusUpdatedBy(who);
    }

    private PaymentMode modeFor(Order order) {
        if (order.getPaymentMethod() == null) {
            return PaymentMode.CASH;
        }
        try {
            return PaymentMode.valueOf(order.getPaymentMethod().toUpperCase());
        } catch (IllegalArgumentException e) {
            return PaymentMode.CASH;
        }
    }

    /**
     * Flattens a payment into just the fields the payments table needs, so the
     * list endpoint stops dragging the whole order, its items and the customer
     * record along with every row.
     */
    private List<Map<String, Object>> summarise(List<Payment> payments) {
        List<Map<String, Object>> out = new ArrayList<>(payments.size());
        for (Payment p : payments) {
            Map<String, Object> row = new LinkedHashMap<>();
            Order o = p.getOrder();
            Customer c = o != null ? o.getCustomer() : null;

            row.put("id", p.getId());
            row.put("orderId", o != null ? o.getId() : null);
            row.put("orderStatus", o != null ? o.getStatus() : null);
            row.put("customerId", c != null ? c.getId() : null);
            row.put("customerName", c != null ? c.getName() : null);
            row.put("customerEmail", c != null ? c.getEmail() : null);
            row.put("paymentMode", p.getPaymentMode());
            row.put("paymentStatus", p.getPaymentStatus());
            row.put("amount", p.getAmount());
            row.put("totalAmount", o != null ? o.getTotalAmount() : null);
            row.put("transactionRef", p.getTransactionRef());
            row.put("transactionDate", p.getTransactionDate());
            row.put("statusUpdatedAt", p.getStatusUpdatedAt());
            row.put("statusUpdatedBy", p.getStatusUpdatedBy());
            row.put("nextStatuses", p.getPaymentStatus() == null
                    ? List.of()
                    : p.getPaymentStatus().allowedNext().stream().map(Enum::name).toList());
            out.add(row);
        }
        return out;
    }
}