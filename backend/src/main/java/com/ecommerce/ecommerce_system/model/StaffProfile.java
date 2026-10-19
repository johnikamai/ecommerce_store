package com.ecommerce.ecommerce_system.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * Links a {@link User} (role STAFF) to the staff-management lifecycle: department,
 * approval status and granted permissions. A missing profile means a "legacy" staff
 * account (role changed directly by an admin) which keeps its pre-existing access.
 */
@Entity
@Table(name = "staff_profiles")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StaffProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    @JsonIgnoreProperties({"password", "authorities"})
    private User user;

    @Column(nullable = false)
    private String fullName;

    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Department department;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private StaffStatus status = StaffStatus.PENDING;

    @Column(length = 1000)
    private String experience;

    @Column(length = 1000)
    private String reason;

    /** Note set by an admin when requesting more information or rejecting. */
    @Column(length = 1000)
    private String decisionReason;

    /** Username of the admin who last changed the status. */
    private String decidedBy;

    private java.time.LocalDateTime decidedAt;

    /** True when the account was created by an admin invite rather than a public application. */
    private boolean invited = false;

    @ElementCollection(fetch = FetchType.EAGER, targetClass = Permission.class)
    @CollectionTable(name = "staff_permissions", joinColumns = @JoinColumn(name = "staff_profile_id"))
    @Column(name = "permission", length = 64)
    @Enumerated(EnumType.STRING)
    private java.util.Set<Permission> permissions = new java.util.HashSet<>();

    private java.time.LocalDateTime createdAt = java.time.LocalDateTime.now();

    private java.time.LocalDateTime updatedAt = java.time.LocalDateTime.now();
}
