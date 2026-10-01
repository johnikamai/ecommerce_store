package com.ecommerce.ecommerce_system.controller;

import com.ecommerce.ecommerce_system.model.ReturnRequest;
import com.ecommerce.ecommerce_system.repository.ReturnRequestRepository;
import com.ecommerce.ecommerce_system.service.ReturnService;
import com.ecommerce.ecommerce_system.security.CustomerGuard;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/returns")
public class ReturnController {

    @Autowired
    private ReturnService returnService;

    @Autowired
    private ReturnRequestRepository returnRequestRepository;

    @Autowired
    private CustomerGuard customerGuard;

    // Customer submits a return request.
    // Body: { "orderItemId": 3, "customerId": 1, "reason": "defective" }
    @PostMapping
    public ResponseEntity<?> requestReturn(@RequestBody Map<String, Object> body, Authentication auth) {
        try {
            Long orderItemId = Long.valueOf(body.get("orderItemId").toString());
            Long customerId = Long.valueOf(body.get("customerId").toString());
            if (!customerGuard.canAccess(customerId, auth)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Access denied");
            }
            String reason = body.get("reason") == null ? null : body.get("reason").toString();
            ReturnRequest created = returnService.requestReturn(orderItemId, customerId, reason);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // Admin: list all return requests (optionally filter by status).
    @GetMapping
    public List<ReturnRequest> getAll(@RequestParam(required = false) String status) {
        if (status == null || status.isBlank()) {
            return returnRequestRepository.findAll();
        }
        return returnRequestRepository.findByStatus(com.ecommerce.ecommerce_system.model.ReturnStatus.valueOf(status.toUpperCase()));
    }

    // Customer: list their own returns.
    @GetMapping("/customer/{customerId}")
    public ResponseEntity<?> getForCustomer(@PathVariable Long customerId, Authentication auth) {
        if (!customerGuard.canAccess(customerId, auth)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Access denied");
        }
        return ResponseEntity.ok(returnRequestRepository.findByCustomerId(customerId));
    }

    // Admin approves -> stock restored + restock notifications fired.
    @PutMapping("/{id}/approve")
    public ResponseEntity<?> approve(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(returnService.process(id, true));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // Admin rejects.
    @PutMapping("/{id}/reject")
    public ResponseEntity<?> reject(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(returnService.process(id, false));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // Admin marks the refund as paid out.
    @PutMapping("/{id}/refund")
    public ResponseEntity<?> markRefunded(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(returnService.markRefunded(id));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
