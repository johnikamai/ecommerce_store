package com.ecommerce.order.controller;

import com.ecommerce.order.model.GroupOrderStatus;
import com.ecommerce.order.service.GroupOrderService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Admin views for group orders. Secured by SecurityConfig (/api/admin/** →
 * ROLE_ADMIN). Admins can observe every group and cancel a pre-placement one;
 * once a group has become a real order, policy says deal with that order
 * through the normal Orders screens.
 */
@RestController
@RequestMapping("/api/admin/group-orders")
public class AdminGroupOrderController {

    private final GroupOrderService groupOrderService;

    public AdminGroupOrderController(GroupOrderService groupOrderService) {
        this.groupOrderService = groupOrderService;
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> list(@RequestParam(required = false) GroupOrderStatus status) {
        return ResponseEntity.ok(groupOrderService.listAll(status));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> detail(@PathVariable Long id, Authentication auth) {
        try {
            return ResponseEntity.ok(groupOrderService.detail(auth, id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.ok(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<?> cancel(@PathVariable Long id, Authentication auth) {
        try {
            return ResponseEntity.ok(groupOrderService.adminCancel(auth, id));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
