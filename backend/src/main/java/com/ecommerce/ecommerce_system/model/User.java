package com.ecommerce.ecommerce_system.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    // Verified via OTP during registration so accounts attach to real addresses.
    @Column(unique = true)
    private String email;

    @Column(nullable = false)
    private String password; // stored as a BCrypt hash, never plain text

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    // New registrations start disabled and can only log in after their OTP is
    // verified. Existing users created before this column stay enabled.
    @Column(nullable = false)
    private boolean enabled = true;

    // Links this login to a customer profile (wishlist, orders, points...).
    // Set for registered customers; null for admins.
    private Long customerId;
}