package com.ecommerce.recommendation.model;

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


@JsonIgnoreProperties(ignoreUnknown=true)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderStatusEvent {

    
    
    private Long id;

    
    
    // Ignored for the same reason as on Order: events reach the client only via
    // the tracking endpoint, which picks the fields it needs. Without this the
    // parent order would be dragged back in if an event were ever serialized
    // on its own, recursing through orderItems.
    @JsonIgnore
    @lombok.EqualsAndHashCode.Exclude
    @lombok.ToString.Exclude
    private Order order;

    
    private OrderStatus status;

    /** Free-text scan line from the courier, e.g. "Departed hub". */
    private String note;

    /** Where the parcel was when the scan happened, e.g. "Bengaluru, KA". */
    private String location;

    private LocalDateTime createdAt = LocalDateTime.now();
}

