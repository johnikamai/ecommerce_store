package com.ecommerce.ecommerce_system.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * A stock alert addressed to store staff. Customer-facing messages go through
 * Notification instead, which is scoped to a single customer.
 *
 * Types: LOW_STOCK, OUT_OF_STOCK, RESTOCKED.
 */
@Entity
@Table(name = "admin_alerts")
@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminAlert {

    public static final String LOW_STOCK = "LOW_STOCK";
    public static final String OUT_OF_STOCK = "OUT_OF_STOCK";
    public static final String RESTOCKED = "RESTOCKED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long productId;

    private String productName;

    private String type;

    private String message;

    // The stock level that triggered the alert.
    private Integer stockAtAlert;

    // The threshold it was measured against.
    private Integer reorderLevel;

    private Boolean isRead = false;

    private LocalDateTime createdAt = LocalDateTime.now();
}