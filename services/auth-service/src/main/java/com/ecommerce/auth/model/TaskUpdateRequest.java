package com.ecommerce.auth.model;

import lombok.Data;

/** Staff update to their own assigned task. */
@Data
public class TaskUpdateRequest {
    private TaskStatus status;
    private String progressNote;
}
