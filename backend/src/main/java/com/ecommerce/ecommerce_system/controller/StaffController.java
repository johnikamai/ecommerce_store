package com.ecommerce.ecommerce_system.controller;

import com.ecommerce.ecommerce_system.model.*;
import com.ecommerce.ecommerce_system.repository.UserRepository;
import com.ecommerce.ecommerce_system.service.ApprovalService;
import com.ecommerce.ecommerce_system.service.StaffNotificationService;
import com.ecommerce.ecommerce_system.service.StaffService;
import com.ecommerce.ecommerce_system.service.StaffTaskService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Staff self-service endpoints. All require a signed-in STAFF/ADMIN account, and
 * individual actions additionally require the relevant PERM_* authority.
 */
@RestController
@RequestMapping("/api/staff")
@PreAuthorize("hasAnyRole('ADMIN','STAFF')")
public class StaffController {

    private final StaffService staffService;
    private final StaffTaskService taskService;
    private final ApprovalService approvalService;
    private final StaffNotificationService notificationService;
    private final UserRepository userRepository;

    public StaffController(StaffService staffService,
                           StaffTaskService taskService,
                           ApprovalService approvalService,
                           StaffNotificationService notificationService,
                           UserRepository userRepository) {
        this.staffService = staffService;
        this.taskService = taskService;
        this.approvalService = approvalService;
        this.notificationService = notificationService;
        this.userRepository = userRepository;
    }

    private User currentUser(Authentication auth) {
        return userRepository.findByUsername(auth.getName())
                .orElseThrow(() -> new NoSuchElementException("Signed-in user not found"));
    }

    private List<String> authorities(Authentication auth) {
        List<String> perms = new ArrayList<>();
        if (auth == null) return perms;
        for (GrantedAuthority authority : auth.getAuthorities()) {
            String name = authority.getAuthority();
            if (name.startsWith(Permission.AUTHORITY_PREFIX)) {
                perms.add(name.substring(Permission.AUTHORITY_PREFIX.length()));
            }
        }
        return perms;
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(Authentication auth) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("username", auth.getName());
        map.put("authorities", auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList());
        map.put("permissions", authorities(auth));
        staffService.findByUsername(auth.getName()).ifPresentOrElse(profile -> {
            map.put("id", profile.getId());
            map.put("fullName", profile.getFullName());
            map.put("email", profile.getUser().getEmail());
            map.put("phone", profile.getPhone());
            map.put("department", profile.getDepartment());
            map.put("status", profile.getStatus());
            map.put("experience", profile.getExperience());
            map.put("createdAt", profile.getCreatedAt());
            map.put("legacy", false);
        }, () -> {
            map.put("legacy", true);
            map.put("role", "STAFF");
        });
        return ResponseEntity.ok(map);
    }

    @GetMapping("/dashboard")
    public ResponseEntity<?> dashboard(Authentication auth) {
        User user = currentUser(auth);
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("tasks", taskService.staffSummary(user.getId()));
        map.put("openTasks", taskService.forUser(user.getId()).stream()
                .filter(t -> t.getStatus() != TaskStatus.COMPLETED && t.getStatus() != TaskStatus.CANCELLED)
                .toList());
        map.put("unreadNotifications", notificationService.unreadCount(user.getId()));
        staffService.findByUsername(auth.getName()).ifPresent(p -> {
            map.put("department", p.getDepartment());
            map.put("status", p.getStatus());
            map.put("fullName", p.getFullName());
        });
        return ResponseEntity.ok(map);
    }

    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_TASK_VIEW')")
    @GetMapping("/tasks")
    public ResponseEntity<?> myTasks(Authentication auth) {
        return ResponseEntity.ok(taskService.forUser(currentUser(auth).getId()));
    }

    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_TASK_VIEW')")
    @PutMapping("/tasks/{id}")
    public ResponseEntity<?> updateTask(@PathVariable Long id,
                                        @RequestBody TaskUpdateRequest request,
                                        Authentication auth) {
        return ResponseEntity.ok(taskService.updateStatus(id, request, auth.getName(), false));
    }

    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_PRICE_CHANGE_REQUEST')")
    @GetMapping("/approvals")
    public ResponseEntity<?> myApprovals(Authentication auth) {
        return ResponseEntity.ok(approvalService.listForUser(currentUser(auth).getId()));
    }

    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_PRICE_CHANGE_REQUEST')")
    @PostMapping("/approvals")
    public ResponseEntity<?> createApproval(@RequestBody ApprovalRequestDto request, Authentication auth) {
        return ResponseEntity.status(201).body(approvalService.create(request, currentUser(auth)));
    }

    @GetMapping("/notifications")
    public ResponseEntity<?> notifications(Authentication auth) {
        User user = currentUser(auth);
        return ResponseEntity.ok(Map.of(
                "items", notificationService.forUser(user.getId()),
                "unread", notificationService.unreadCount(user.getId())));
    }

    @PutMapping("/notifications/read-all")
    public ResponseEntity<?> readAllNotifications(Authentication auth) {
        User user = currentUser(auth);
        int updated = notificationService.markAllRead(user.getId());
        return ResponseEntity.ok(Map.of("updated", updated, "unread", 0));
    }
}
