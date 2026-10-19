package com.ecommerce.ecommerce_system.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Immutable audit trail of staff- and admin-initiated actions. */
@Entity
@Table(name = "staff_activity_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StaffActivityLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Username of the person who performed the action. */
    @Column(nullable = false)
    private String actorUsername;

    /** Short action code, e.g. STAFF_APPROVED, TASK_ASSIGNED, PRICE_APPROVED. */
    @Column(nullable = false, length = 64)
    private String action;

    @Column(length = 64)
    private String targetType;

    private Long targetId;

    @Column(length = 2000)
    private String details;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
