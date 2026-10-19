package com.ecommerce.ecommerce_system.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** A member of a group order, their computed share and settlement state. */
@Entity
@Table(name = "group_order_members",
        uniqueConstraints = @UniqueConstraint(name = "uk_group_member", columnNames = {"group_order_id", "customer_id"}),
        indexes = @Index(name = "idx_group_member_customer", columnList = "customer_id"))
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GroupOrderMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_order_id", nullable = false)
    @JsonIgnore
    private GroupOrder groupOrder;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    /** "HOST" for the creator, "MEMBER" for everyone else. */
    @Column(nullable = false, length = 10)
    private String role = "MEMBER";

    private LocalDateTime joinedAt = LocalDateTime.now();

    /** This member's payable share in the current split, null until computed. */
    private BigDecimal shareAmount;

    /**
     * Split version this member last confirmed. Reaching the group's current
     * splitVersion is required before checkout, so a reprice forces reapproval.
     */
    @Column(nullable = false, columnDefinition = "int default -1")
    private int confirmedSplitVersion = -1;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GroupShareStatus shareStatus = GroupShareStatus.PENDING;

    public boolean isHost() {
        return "HOST".equals(role);
    }

    public boolean hasConfirmed(int currentSplitVersion) {
        return confirmedSplitVersion == currentSplitVersion && shareAmount != null;
    }
}
