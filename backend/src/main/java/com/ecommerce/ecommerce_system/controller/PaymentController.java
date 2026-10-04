package com.ecommerce.ecommerce_system.controller;

import com.ecommerce.ecommerce_system.model.*;
import com.ecommerce.ecommerce_system.repository.OrderRepository;
import com.ecommerce.ecommerce_system.repository.PaymentRepository;
import com.ecommerce.ecommerce_system.security.CustomerGuard;
import com.ecommerce.ecommerce_system.service.NotificationService;
import com.ecommerce.ecommerce_system.service.ReceiptService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
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

    @Autowired private com.ecommerce.ecommerce_system.service.OrderService orderService;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private ReceiptService receiptService;

    @Autowired
    private CustomerGuard customerGuard;

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
        return ResponseEntity.ok(summarise(paymentRepository.findByOrder_CustomerId(customerId)));
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

    @PostMapping
    @Transactional
    public ResponseEntity<?> create(@RequestBody Payment payment, Authentication auth) {
        if (payment.getOrder() == null || payment.getOrder().getId() == null) {
            return ResponseEntity.badRequest().body("Order is required");
        }
        Long orderId = payment.getOrder().getId();
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            return ResponseEntity.badRequest().body("Order not found: " + orderId);
        }

        // Payment -> order is a OneToOne; a duplicate would otherwise surface as a
        // raw SQL constraint violation (HTTP 500). Reject it cleanly instead.
        if (paymentRepository.findByOrderId(orderId).isPresent()) {
            return ResponseEntity.badRequest().body("A payment already exists for order " + orderId);
        }

        payment.setOrder(order);

        // The amount is always the order's own total - never a client-supplied figure.
        payment.setId(null);
        payment.setAmount(order.getTotalAmount());

        if (payment.getPaymentStatus() == null) {
            payment.setPaymentStatus(PaymentStatus.PENDING);
        }
        if (payment.getPaymentMode() == null) {
            payment.setPaymentMode(modeFor(order));
        }
        if (payment.getTransactionDate() == null) {
            payment.setTransactionDate(LocalDateTime.now());
        }
        audit(payment, auth == null ? "system" : auth.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(paymentRepository.save(payment));
    }

    // PUT /api/payments/{id}/status  { "status": "PAID", "transactionRef": "UPI123" }
    @PutMapping("/{id}/status")
    @Transactional
    public ResponseEntity<?> updateStatus(@PathVariable Long id,
                                          @RequestBody Map<String, String> body,
                                          Authentication auth) {
        String status = body.get("status");
        if (status == null || status.isBlank()) {
            return ResponseEntity.badRequest().body("Status is required");
        }

        PaymentStatus newStatus;
        try {
            newStatus = PaymentStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body("Invalid status: " + status
                    + ". Expected one of " + Arrays.toString(PaymentStatus.values()));
        }

        Optional<Payment> found = paymentRepository.findById(id);
        if (found.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Payment payment = found.get();
        PaymentStatus current = payment.getPaymentStatus();

        if (current == newStatus) {
            // Idempotent re-send, but still let a reference number be attached.
            if (body.get("transactionRef") != null) {
                payment.setTransactionRef(body.get("transactionRef").trim());
                audit(payment, auth == null ? "system" : auth.getName());
                paymentRepository.save(payment);
            }
            return ResponseEntity.ok(payment);
        }

        if (!current.canTransitionTo(newStatus)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(
                    "Cannot change payment from " + current + " to " + newStatus
                            + ". Allowed next: " + (current.allowedNext().isEmpty()
                            ? "none - this is a final state"
                            : current.allowedNext()));
        }

        payment.setPaymentStatus(newStatus);
        if (body.get("transactionRef") != null && !body.get("transactionRef").isBlank()) {
            payment.setTransactionRef(body.get("transactionRef").trim());
        }
        audit(payment, auth == null ? "system" : auth.getName());
        paymentRepository.save(payment);

        // Keep the order in sync so the customer sees the correct payment state.
        Order order = payment.getOrder();
        if (order != null) {
            order.setPaymentStatus(newStatus);
            orderRepository.save(order);
            if (newStatus == PaymentStatus.REFUNDED) orderService.reverseRewards(order);
            if (order.getCustomer() != null) {
                String mode = payment.getPaymentMode() != null ? payment.getPaymentMode().name() : "UNKNOWN";
                notificationService.paymentMade(order.getCustomer(), order.getId(), mode, newStatus.name());
            }
        }
        return ResponseEntity.ok(payment);
    }

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