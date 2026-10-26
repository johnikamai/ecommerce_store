package com.ecommerce.product.controller;

import com.ecommerce.product.model.AdminAlert;
import com.ecommerce.product.repo.AdminAlertRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Stock alerts for store staff. Customer-facing notices live under
 * /api/notifications instead.
 */
@RestController
@RequestMapping("/api/admin/alerts")
public class AdminAlertController {

    private final AdminAlertRepository adminAlertRepository;

    public AdminAlertController(AdminAlertRepository adminAlertRepository) {
        this.adminAlertRepository = adminAlertRepository;
    }

    @GetMapping
    public Map<String, Object> list() {
        List<AdminAlert> alerts = adminAlertRepository.findTop50ByOrderByCreatedAtDesc();
        Map<String, Object> body = new HashMap<>();
        body.put("unread", adminAlertRepository.countUnread());
        body.put("alerts", alerts);
        return body;
    }

    @PutMapping("/{id}/read")
    public ResponseEntity<?> markRead(@PathVariable Long id, @RequestBody Map<String, Boolean> payload) {
        return adminAlertRepository.findById(id)
                .map(alert -> {
                    if (payload != null && Boolean.FALSE.equals(payload.get("read"))) {
                        return ResponseEntity.badRequest().body("read flag must be true");
                    }
                    alert.setIsRead(true);
                    return ResponseEntity.ok(adminAlertRepository.save(alert));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/read-all")
    public Map<String, Object> markAllRead() {
        List<AdminAlert> unread = adminAlertRepository.findAll().stream()
                .filter(a -> !Boolean.TRUE.equals(a.getIsRead()))
                .toList();
        unread.forEach(a -> a.setIsRead(true));
        adminAlertRepository.saveAll(unread);
        return Map.of("updated", unread.size(), "unread", 0);
    }
}
