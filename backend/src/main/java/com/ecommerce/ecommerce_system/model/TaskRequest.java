package com.ecommerce.ecommerce_system.model;

import lombok.Data;

import java.time.LocalDate;

/** Admin payload for creating or reassigning a task. */
@Data
public class TaskRequest {
    private String title;
    private String description;
    private Long assignedToUserId;
    private TaskPriority priority;
    private LocalDate dueDate;
    private String adminNotes;
}
