package com.ecommerce.order.controller;
import com.ecommerce.order.model.*;
import com.ecommerce.order.repo.*;
import com.ecommerce.order.service.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.http.ResponseEntity;
import java.util.*;
/** Coordinates order/payment consistency; the payment mutation remains payment-owned. */
@RestController @RequestMapping("/api/payments") @PreAuthorize("hasRole('ADMIN')")
public class PaymentCommandController {
    private final CheckoutCoordinator coordinator;private final PaymentRepository payments;private final OrderHydrator orders;
    public PaymentCommandController(CheckoutCoordinator coordinator,PaymentRepository payments,OrderHydrator orders){this.coordinator=coordinator;this.payments=payments;this.orders=orders;}
    @PutMapping("/{id}/status")public Object status(@PathVariable Long id,@RequestBody Map<String,String> body,Authentication auth){return coordinator.paymentStatus(id,PaymentStatus.valueOf(body.get("status").trim().toUpperCase()),body.get("transactionRef"),auth.getName());}
    @PostMapping public ResponseEntity<?> create(@RequestBody Payment request){if(request.getOrder()==null||request.getOrder().getId()==null)throw new IllegalArgumentException("Order is required");return ResponseEntity.status(201).body(payments.create(orders.load(request.getOrder().getId())));}
}
