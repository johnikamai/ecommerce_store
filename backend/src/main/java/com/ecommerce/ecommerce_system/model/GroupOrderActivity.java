package com.ecommerce.ecommerce_system.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** One audit event on a group order; feeds the live activity list. */
@Entity
@Table(name = "group_order_activities",
        indexes = @Index(name = "idx_group_activity_group", columnList = "group_order_id"))
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GroupOrderActivity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_order_id", nullable = false)
    @JsonIgnore
    private GroupOrder groupOrder;

    /** Who did it; null for system-generated events (e.g. checkout started). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id")
    private Customer actor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GroupActivityType type;

    @Column(nullable = false, length = 300)
    private String message;

    private LocalDateTime createdAt = LocalDateTime.now();
}
