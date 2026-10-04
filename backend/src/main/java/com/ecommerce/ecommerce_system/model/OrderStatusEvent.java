package com.ecommerce.ecommerce_system.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * One scan in an order's delivery journey.
 *
 * Stored rather than derived so the customer-facing tracker can show what
 * actually happened and when, including the courier's own scans. Rows are
 * appended by OrderService on every status change and by the admin console when
 * a parcel is handed to a carrier, and are never edited afterwards.
 */
@Entity
@Table(name = "order_status_events", indexes = @Index(name = "idx_order_event_order", columnList = "order_id"))
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderStatusEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    // Ignored for the same reason as on Order: events reach the client only via
    // the tracking endpoint, which picks the fields it needs. Without this the
    // parent order would be dragged back in if an event were ever serialized
    // on its own, recursing through orderItems.
    @JsonIgnore
    private Order order;

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    /** Free-text scan line from the courier, e.g. "Departed hub". */
    private String note;

    /** Where the parcel was when the scan happened, e.g. "Bengaluru, KA". */
    private String location;

    private LocalDateTime createdAt = LocalDateTime.now();
}
