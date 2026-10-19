package com.ecommerce.ecommerce_system.controller;

import com.ecommerce.ecommerce_system.model.*;
import com.ecommerce.ecommerce_system.service.ApprovalService;
import com.ecommerce.ecommerce_system.service.StaffActivityService;
import com.ecommerce.ecommerce_system.service.StaffService;
import com.ecommerce.ecommerce_system.service.StaffTaskService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Admin-only staff management: review applications, invite staff, manage
 * roles/permissions and status, assign tasks, action approval requests and read
 * the audit trail.
 */
@RestController
@RequestMapping("/api/admin/staff")
@PreAuthorize("hasRole('ADMIN')")
public class AdminStaffController {

    private final StaffService staffService;
    private final StaffTaskService taskService;
    private final ApprovalService approvalService;
    private final StaffActivityService activityService;

    public AdminStaffController(StaffService staffService,
                                StaffTaskService taskService,
                                ApprovalService approvalService,
                                StaffActivityService activityService) {
        this.staffService = staffService;
        this.taskService = taskService;
        this.approvalService = approvalService;
        this.activityService = activityService;
    }

    // ------------------------------------------------------------- staff accounts

    @GetMapping("/summary")
    public ResponseEntity<?> summary() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("staff", staffService.summary());
        map.put("tasks", taskService.adminSummary());
        map.put("approvals", approvalService.summary());
        return ResponseEntity.ok(map);
    }

    @GetMapping
    public ResponseEntity<?> list(@RequestParam(required = false) StaffStatus status,
                                  @RequestParam(required = false) String q) {
        if (q != null && !q.isBlank()) {
            return ResponseEntity.ok(staffService.search(q));
        }
        return ResponseEntity.ok(staffService.list(status));
    }

    @GetMapping("/assignable")
    public ResponseEntity<?> assignable() {
        return ResponseEntity.ok(staffService.approved().stream().map(p -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("userId", p.getUser().getId());
            m.put("username", p.getUser().getUsername());
            m.put("fullName", p.getFullName());
            m.put("department", p.getDepartment());
            return m;
        }).toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable Long id) {
        return ResponseEntity.ok(staffService.getById(id));
    }

    @PostMapping("/invite")
    public ResponseEntity<?> invite(@RequestBody StaffInviteRequest request, Authentication auth) {
        return ResponseEntity.status(201).body(staffService.createInvite(request, auth.getName()));
    }

    @PostMapping("/{id}/decision")
    public ResponseEntity<?> decide(@PathVariable Long id,
                                    @RequestBody StaffDecisionRequest request,
                                    Authentication auth) {
        return ResponseEntity.ok(staffService.decide(id, request, auth.getName()));
    }

    @PutMapping("/{id}/role")
    public ResponseEntity<?> updateRole(@PathVariable Long id,
                                        @RequestBody StaffDecisionRequest request,
                                        Authentication auth) {
        return ResponseEntity.ok(staffService.updateRole(id, request.getDepartment(), request.getPermissions(), auth.getName()));
    }

    // ------------------------------------------------------------- tasks

    @GetMapping("/tasks")
    public ResponseEntity<?> tasks(@RequestParam(required = false) TaskStatus status) {
        return ResponseEntity.ok(status == null ? taskService.all() : taskService.byStatus(status));
    }

    @PostMapping("/tasks")
    public ResponseEntity<?> createTask(@RequestBody TaskRequest request, Authentication auth) {
        return ResponseEntity.status(201).body(taskService.create(request, auth.getName()));
    }

    @PutMapping("/tasks/{id}")
    public ResponseEntity<?> updateTask(@PathVariable Long id,
                                        @RequestBody TaskUpdateRequest request,
                                        Authentication auth) {
        return ResponseEntity.ok(taskService.updateStatus(id, request, auth.getName(), true));
    }

    @PutMapping("/tasks/{id}/reassign")
    public ResponseEntity<?> reassign(@PathVariable Long id,
                                      @RequestParam Long userId,
                                      Authentication auth) {
        return ResponseEntity.ok(taskService.reassign(id, userId, auth.getName()));
    }

    @DeleteMapping("/tasks/{id}")
    public ResponseEntity<?> deleteTask(@PathVariable Long id, Authentication auth) {
        taskService.delete(id, auth.getName());
        return ResponseEntity.noContent().build();
    }

    // ------------------------------------------------------------- approvals

    @GetMapping("/approvals")
    public ResponseEntity<?> approvals(@RequestParam(required = false) ApprovalStatus status) {
        return ResponseEntity.ok(approvalService.list(status));
    }

    @PostMapping("/approvals/{id}/decision")
    public ResponseEntity<?> decideApproval(@PathVariable Long id,
                                            @RequestBody Map<String, Object> body,
                                            Authentication auth) {
        boolean approve = Boolean.TRUE.equals(body.get("approve"));
        String reason = body.get("reason") == null ? null : body.get("reason").toString();
        return ResponseEntity.ok(approvalService.decide(id, approve, reason, auth.getName()));
    }

    // ------------------------------------------------------------- audit

    @GetMapping("/logs")
    public ResponseEntity<?> logs(@RequestParam(required = false) String actor) {
        return ResponseEntity.ok(actor == null || actor.isBlank()
                ? activityService.recent()
                : activityService.byActor(actor));
    }

    @GetMapping("/logs/{targetType}/{targetId}")
    public ResponseEntity<?> logsForTarget(@PathVariable String targetType, @PathVariable Long targetId) {
        return ResponseEntity.ok(activityService.byTarget(targetType, targetId));
    }
}
