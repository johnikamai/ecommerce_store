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
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private CustomerGuard customerGuard;

    @GetMapping("/customer/{customerId}")
    public ResponseEntity<?> getForCustomer(@PathVariable Long customerId, Authentication auth) {
        if (!customerGuard.canAccess(customerId, auth)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Access denied");
        }
        return ResponseEntity.ok(notificationRepository.findByCustomerIdOrderBySentAtDesc(customerId));
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

    // Mark all of a customer's notifications as read.
    @PutMapping("/customer/{customerId}/read-all")
    public ResponseEntity<?> markAllAsRead(@PathVariable Long customerId, Authentication auth) {
        if (!customerGuard.canAccess(customerId, auth)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Access denied");
        }
        for (Notification n : notificationRepository.findByCustomerIdOrderBySentAtDesc(customerId)) {
            if (!Boolean.TRUE.equals(n.getIsRead())) {
                n.setIsRead(true);
                notificationRepository.save(n);
            }
        }
        return ResponseEntity.ok("All notifications marked as read");
    }
}
