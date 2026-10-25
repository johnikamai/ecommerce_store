package com.ecommerce.auth.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Staff-facing notification. Kept separate from the customer notification
 * table because that one has a non-null foreign key to Customer.
 */
@Entity
@Table(name = "staff_notifications")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StaffNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnoreProperties({"password", "authorities"})
    private User user;

    @Column(nullable = false, length = 1000)
    private String message;

    @Column(length = 48)
    private String type;

    private Boolean isRead = false;

    private LocalDateTime sentAt = LocalDateTime.now();
}
