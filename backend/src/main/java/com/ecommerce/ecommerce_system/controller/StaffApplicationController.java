package com.ecommerce.ecommerce_system.controller;

import com.ecommerce.ecommerce_system.model.StaffApplicationRequest;
import com.ecommerce.ecommerce_system.model.StaffProfile;
import com.ecommerce.ecommerce_system.service.StaffService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Public staff-onboarding endpoints. Applying only creates a PENDING profile with
 * no privileges; access is granted exclusively by an admin decision.
 */
@RestController
@RequestMapping("/api/staff")
public class StaffApplicationController {

    private final StaffService staffService;

    public StaffApplicationController(StaffService staffService) {
        this.staffService = staffService;
    }

    @PostMapping("/apply")
    public ResponseEntity<?> apply(@RequestBody StaffApplicationRequest request) {
        StaffProfile profile = staffService.apply(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(toPublicView(profile,
                "Application received. An administrator will review it shortly."));
    }

    @GetMapping("/application-status")
    public ResponseEntity<?> status(@RequestParam String email) {
        Optional<StaffProfile> profile = staffService.findByEmail(email);
        if (profile.isEmpty()) {
            return ResponseEntity.ok(Map.of("found", false));
        }
        return ResponseEntity.ok(toPublicView(profile.get(), null));
    }

    private Map<String, Object> toPublicView(StaffProfile profile, String message) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("found", true);
        map.put("id", profile.getId());
        map.put("fullName", profile.getFullName());
        map.put("email", profile.getUser() != null ? profile.getUser().getEmail() : null);
        map.put("department", profile.getDepartment());
        map.put("status", profile.getStatus());
        map.put("createdAt", profile.getCreatedAt());
        if (message != null) map.put("message", message);
        if (profile.getDecisionReason() != null) map.put("decisionReason", profile.getDecisionReason());
        return map;
    }
}
