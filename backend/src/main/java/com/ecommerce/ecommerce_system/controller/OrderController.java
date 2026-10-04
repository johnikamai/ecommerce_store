package com.ecommerce.ecommerce_system.controller;

import com.ecommerce.ecommerce_system.model.Order;
import com.ecommerce.ecommerce_system.model.OrderRequest;
import com.ecommerce.ecommerce_system.model.OrderStatus;
import com.ecommerce.ecommerce_system.repository.OrderRepository;
import com.ecommerce.ecommerce_system.security.CustomerGuard;
import com.ecommerce.ecommerce_system.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderService orderService;

    @Autowired
    private CustomerGuard customerGuard;

    @GetMapping
    public List<Order> getAll() {
        return orderRepository.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Order> getById(@PathVariable Long id) {
        return orderRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<?> placeOrder(@RequestBody OrderRequest request, Authentication auth) {
        if (!customerGuard.canAccess(request.getCustomerId(), auth)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("You can only place orders on your own account");
        }
        try {
            Order order = orderService.placeOrder(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(order);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // Customer cancels their own PLACED order (stock gets released).
    @PostMapping("/{id}/cancel")
    public ResponseEntity<?> cancelOrder(@PathVariable Long id, Authentication auth) {
        Order order = orderRepository.findById(id).orElse(null);
        if (order == null) {
            return ResponseEntity.notFound().build();
        }
        Long customerId = order.getCustomer().getId();
        if (!customerGuard.canAccess(customerId, auth)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("You can only cancel your own orders");
        }
        try {
            return ResponseEntity.ok(orderService.cancelOrder(id, customerId));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/customer/{customerId}")
    public ResponseEntity<?> getForCustomer(@PathVariable Long customerId, Authentication auth) {
        if (!customerGuard.canAccess(customerId, auth)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("You can only view your own orders");
        }
        return ResponseEntity.ok(orderRepository.findByCustomerId(customerId));
    }

    /**
     * Delivery timeline for one order.
     *
     * Reachable by the customer who owns the order and by staff, so the same
     * endpoint backs both the storefront tracker and the admin console.
     */
    @GetMapping("/{id}/tracking")
    public ResponseEntity<?> tracking(@PathVariable Long id, Authentication auth) {
        Order order = orderRepository.findById(id).orElse(null);
        if (order == null) {
            return ResponseEntity.notFound().build();
        }
        Long customerId = order.getCustomer() == null ? null : order.getCustomer().getId();
        boolean staff = auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_STAFF"));
        if (!staff && !customerGuard.canAccess(customerId, auth)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("You can only track your own orders");
        }
        return ResponseEntity.ok(orderService.trackingFor(order));
    }

    /**
     * Records courier handover for an order moving outside the normal flow.
     * Admin only, like the status endpoint it complements.
     */
    @PutMapping("/{id}/tracking")
    public ResponseEntity<?> assignTracking(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String trackingNumber = body.get("trackingNumber");
        String carrier = body.get("carrier");
        LocalDateTime expected = null;
        String rawDate = body.get("expectedDelivery");
        if (rawDate != null && !rawDate.isBlank()) {
            try {
                expected = LocalDateTime.parse(rawDate);
            } catch (DateTimeParseException e) {
                return ResponseEntity.badRequest()
                        .body("expectedDelivery must be ISO-8601, e.g. 2026-10-09T18:00:00");
            }
        }
        try {
            return ResponseEntity.ok(orderService.assignTracking(id, carrier, trackingNumber, expected));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // Admin (and internal shipping flow) moves an order through its lifecycle.
    @PutMapping("/{id}/status")
    public ResponseEntity<?> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String rawStatus = body.get("status");
        if (rawStatus == null || rawStatus.isBlank()) {
            return ResponseEntity.badRequest().body("Status is required");
        }
        OrderStatus newStatus;
        try {
            newStatus = OrderStatus.valueOf(rawStatus.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body("Invalid status: " + rawStatus);
        }
        // Illegal transitions (e.g. changing a CANCELLED order) surface their own reason.
        try {
            return ResponseEntity.ok(orderService.updateStatus(id, newStatus));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}