package com.ecommerce.ecommerce_system.controller;

import com.ecommerce.ecommerce_system.model.Customer;
import com.ecommerce.ecommerce_system.model.Notification;
import com.ecommerce.ecommerce_system.repository.CustomerRepository;
import com.ecommerce.ecommerce_system.repository.NotificationRepository;
import com.ecommerce.ecommerce_system.security.CustomerGuard;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private CustomerGuard customerGuard;

    // GET /api/notifications/customer/{customerId}
    // Returns the newest 50 plus an unread count, so the bell panel does not pull a
    // customer's whole notification history on every page load.
    @GetMapping("/customer/{customerId}")
    public ResponseEntity<?> getForCustomer(@PathVariable Long customerId, Authentication auth) {
        if (!customerGuard.canAccess(customerId, auth)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Access denied");
        }
        Map<String, Object> body = new HashMap<>();
        body.put("notifications", notificationRepository.findTop50ByCustomerIdOrderBySentAtDesc(customerId));
        body.put("unread", notificationRepository.countByCustomerIdAndIsReadFalse(customerId));
        return ResponseEntity.ok(body);
    }

    // GET /api/notifications/customer/{customerId}/unread-count
    @GetMapping("/customer/{customerId}/unread-count")
    public ResponseEntity<?> unreadCount(@PathVariable Long customerId, Authentication auth) {
        if (!customerGuard.canAccess(customerId, auth)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Access denied");
        }
        return ResponseEntity.ok(Map.of("unread", notificationRepository.countByCustomerIdAndIsReadFalse(customerId)));
    }

    @PostMapping
    public ResponseEntity<?> send(@RequestBody Notification notification, Authentication auth) {
        if (notification.getCustomer() == null || notification.getCustomer().getId() == null) {
            return ResponseEntity.badRequest().body("Customer is required");
        }
        Long customerId = notification.getCustomer().getId();
        if (!customerGuard.canAccess(customerId, auth)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Access denied");
        }
        Customer customer = customerRepository.findById(customerId).orElse(null);
        if (customer == null) {
            return ResponseEntity.badRequest().body("Customer not found: " + customerId);
        }
    
        notification.setCustomer(customer);

             if (notification.getSentAt() == null) {
        notification.setSentAt(java.time.LocalDateTime.now());
    }
    
        return ResponseEntity.status(HttpStatus.CREATED).body(notificationRepository.save(notification));
    }

    // Customer marks one notification as read (ticks it in the bell panel).
    @PutMapping("/{id}/read")
    public ResponseEntity<?> markAsRead(@PathVariable Long id, Authentication auth) {
        Notification notification = notificationRepository.findById(id).orElse(null);
        if (notification == null) {
            return ResponseEntity.notFound().build();
        }
        if (!customerGuard.canAccess(notification.getCustomer().getId(), auth)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Access denied");
        }
        notification.setIsRead(true);
        return ResponseEntity.ok(notificationRepository.save(notification));
    }

    // Mark all of a customer's notifications as read (single UPDATE).
    @PutMapping("/customer/{customerId}/read-all")
    @Transactional
    public ResponseEntity<?> markAllAsRead(@PathVariable Long customerId, Authentication auth) {
        if (!customerGuard.canAccess(customerId, auth)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Access denied");
        }
        int updated = notificationRepository.markAllRead(customerId);
        return ResponseEntity.ok(Map.of("updated", updated, "unread", 0));
    }
}
