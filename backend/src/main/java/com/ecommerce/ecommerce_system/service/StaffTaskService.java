package com.ecommerce.ecommerce_system.service;

import com.ecommerce.ecommerce_system.model.*;
import com.ecommerce.ecommerce_system.repository.StaffTaskRepository;
import com.ecommerce.ecommerce_system.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/** Task assignment and progress tracking for staff. */
@Service
public class StaffTaskService {

    private final StaffTaskRepository taskRepository;
    private final UserRepository userRepository;
    private final StaffNotificationService notificationService;
    private final StaffActivityService activityService;

    public StaffTaskService(StaffTaskRepository taskRepository,
                            UserRepository userRepository,
                            StaffNotificationService notificationService,
                            StaffActivityService activityService) {
        this.taskRepository = taskRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.activityService = activityService;
    }

    @Transactional
    public StaffTask create(TaskRequest req, String adminUsername) {
        if (req.getTitle() == null || req.getTitle().trim().isEmpty()) {
            throw new IllegalArgumentException("Task title is required");
        }
        StaffTask task = new StaffTask();
        task.setTitle(req.getTitle().trim());
        task.setDescription(req.getDescription());
        task.setPriority(req.getPriority() == null ? TaskPriority.MEDIUM : req.getPriority());
        task.setDueDate(req.getDueDate());
        task.setAdminNotes(req.getAdminNotes());
        task.setStatus(TaskStatus.TODO);
        task.setAssignedBy(adminUsername);
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());

        if (req.getAssignedToUserId() != null) {
            User assignee = userRepository.findById(req.getAssignedToUserId())
                    .orElseThrow(() -> new NoSuchElementException("User not found: " + req.getAssignedToUserId()));
            task.setAssignedTo(assignee);
        }
        StaffTask saved = taskRepository.save(task);
        activityService.record(adminUsername, "TASK_CREATED", "TASK", saved.getId(), saved.getTitle());
        if (saved.getAssignedTo() != null) {
            notificationService.notify(saved.getAssignedTo(),
                    "New task assigned: " + saved.getTitle(), "TASK");
        }
        return saved;
    }

    @Transactional
    public StaffTask reassign(Long taskId, Long userId, String adminUsername) {
        StaffTask task = get(taskId);
        User assignee = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("User not found: " + userId));
        task.setAssignedTo(assignee);
        task.setUpdatedAt(LocalDateTime.now());
        StaffTask saved = taskRepository.save(task);
        activityService.record(adminUsername, "TASK_REASSIGNED", "TASK", saved.getId(),
                "Assigned to " + assignee.getUsername());
        notificationService.notify(assignee, "Task assigned to you: " + saved.getTitle(), "TASK");
        return saved;
    }

    @Transactional
    public StaffTask updateStatus(Long taskId, TaskUpdateRequest req, String actorUsername, boolean admin) {
        StaffTask task = get(taskId);
        if (!admin) {
            if (task.getAssignedTo() == null || !task.getAssignedTo().getUsername().equals(actorUsername)) {
                throw new AccessDeniedException("You can only update tasks assigned to you");
            }
        }
        if (req.getStatus() != null) {
            task.setStatus(req.getStatus());
            if (req.getStatus() == TaskStatus.COMPLETED) {
                task.setCompletedAt(LocalDateTime.now());
            } else {
                task.setCompletedAt(null);
            }
        }
        if (req.getProgressNote() != null) {
            task.setProgressNote(req.getProgressNote());
        }
        task.setUpdatedAt(LocalDateTime.now());
        StaffTask saved = taskRepository.save(task);
        activityService.record(actorUsername, "TASK_UPDATED", "TASK", saved.getId(),
                "Status -> " + saved.getStatus());
        return saved;
    }

    public List<StaffTask> all() {
        return taskRepository.findAllByOrderByCreatedAtDesc();
    }

    public List<StaffTask> forUser(Long userId) {
        return taskRepository.findByAssignedToIdOrderByCreatedAtDesc(userId);
    }

    public List<StaffTask> forUserByStatus(Long userId, TaskStatus status) {
        return taskRepository.findByAssignedToIdAndStatusOrderByCreatedAtDesc(userId, status);
    }

    public List<StaffTask> byStatus(TaskStatus status) {
        return taskRepository.findByStatusOrderByCreatedAtDesc(status);
    }

    public StaffTask get(Long id) {
        return taskRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Task not found: " + id));
    }

    @Transactional
    public void delete(Long id, String adminUsername) {
        StaffTask task = get(id);
        taskRepository.delete(task);
        activityService.record(adminUsername, "TASK_DELETED", "TASK", id, task.getTitle());
    }

    public Map<String, Object> staffSummary(Long userId) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("total", taskRepository.countByAssignedToId(userId));
        map.put("open", taskRepository.countByAssignedToIdAndStatusIn(userId,
                List.of(TaskStatus.TODO, TaskStatus.IN_PROGRESS, TaskStatus.BLOCKED)));
        map.put("completed", taskRepository.countByAssignedToIdAndStatusIn(userId, List.of(TaskStatus.COMPLETED)));
        return map;
    }

    public Map<String, Object> adminSummary() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("total", taskRepository.count());
        map.put("todo", taskRepository.countByStatus(TaskStatus.TODO));
        map.put("inProgress", taskRepository.countByStatus(TaskStatus.IN_PROGRESS));
        map.put("blocked", taskRepository.countByStatus(TaskStatus.BLOCKED));
        map.put("completed", taskRepository.countByStatus(TaskStatus.COMPLETED));
        return map;
    }
}
