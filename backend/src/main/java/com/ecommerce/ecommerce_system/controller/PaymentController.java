package com.ecommerce.ecommerce_system.controller;

import com.ecommerce.ecommerce_system.model.Order;
import com.ecommerce.ecommerce_system.model.Payment;
import com.ecommerce.ecommerce_system.model.PaymentStatus;
import com.ecommerce.ecommerce_system.repository.OrderRepository;
import com.ecommerce.ecommerce_system.repository.PaymentRepository;
import com.ecommerce.ecommerce_system.security.CustomerGuard;
import com.ecommerce.ecommerce_system.service.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private CustomerGuard customerGuard;

    @GetMapping
    public List<Payment> getAll() {
        return paymentRepository.findAll();
    }

    // GET /api/payments/customer/{customerId}  -> own payments for a customer
    @GetMapping("/customer/{customerId}")
    public ResponseEntity<?> getByCustomer(@PathVariable Long customerId, Authentication auth) {
        if (!customerGuard.canAccess(customerId, auth)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Access denied");
        }
        return ResponseEntity.ok(paymentRepository.findByOrder_CustomerId(customerId));
    }

@PostMapping
public ResponseEntity<?> create(@RequestBody Payment payment) {
    if (payment.getOrder() == null || payment.getOrder().getId() == null) {
        return ResponseEntity.badRequest().body("Order is required");
    }
    Long orderId = payment.getOrder().getId();
    Order order = orderRepository.findById(orderId).orElse(null);
    if (order == null) {
        return ResponseEntity.badRequest().body("Order not found: " + orderId);
    }
    payment.setOrder(order);
    if (payment.getAmount() == null) {
        payment.setAmount(order.getTotalAmount());
    }
    if (payment.getPaymentStatus() == null) {
        payment.setPaymentStatus(PaymentStatus.PENDING);
    }
    if (payment.getTransactionDate() == null) {
        payment.setTransactionDate(java.time.LocalDateTime.now());
    }
    return ResponseEntity.status(HttpStatus.CREATED).body(paymentRepository.save(payment));
}

    // PUT /api/payments/{id}/status  { "status": "PAID" }
    @PutMapping("/{id}/status")
    public ResponseEntity<?> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return paymentRepository.findById(id)
                .map(payment -> {
                    String status = body.get("status");
                    if (status == null || status.isBlank()) {
                        return ResponseEntity.badRequest().body("Status is required");
                    }
                    try {
                        PaymentStatus newStatus = PaymentStatus.valueOf(status.toUpperCase());
                        payment.setPaymentStatus(newStatus);
                        paymentRepository.save(payment);

                        // Keep the order in sync so the customer sees the correct payment state.
                        Order order = payment.getOrder();
                        if (order != null) {
                            order.setPaymentStatus(newStatus);
                            orderRepository.save(order);
                            if (order.getCustomer() != null) {
                                String mode = payment.getPaymentMode() != null ? payment.getPaymentMode().name() : "UNKNOWN";
                                notificationService.paymentMade(order.getCustomer(), order.getId(), mode, newStatus.name());
                            }
                        }
                        return ResponseEntity.ok(payment);
                    } catch (IllegalArgumentException e) {
                        return ResponseEntity.badRequest().body("Invalid status: " + status);
                    }
                })
                .orElse(ResponseEntity.notFound().build());
    }
}