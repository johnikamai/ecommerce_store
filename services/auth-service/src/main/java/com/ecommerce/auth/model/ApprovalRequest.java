package com.ecommerce.auth.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A sensitive change requested by staff (price change / discount) that only becomes
 * effective once an admin approves it. Approval executes the change transactionally
 * and records who did what and when.
 */
@Entity
@Table(name = "approval_requests")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ApprovalType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ApprovalStatus status = ApprovalStatus.PENDING;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "requested_by_user_id")
    @JsonIgnoreProperties({"password", "authorities"})
    private User requestedBy;

    private String requestedByUsername;

    private Long productId;

    private String productName;

    /** Price before the change, captured when the request was created. */
    private BigDecimal previousPrice;

    /** Absolute new price (PRICE_CHANGE). */
    private BigDecimal requestedPrice;

    /** Percentage discount to apply to the current price (DISCOUNT). */
    private Integer discountPercent;

    @Column(length = 1000)
    private String reason;

    private String decidedBy;
    private LocalDateTime decidedAt;

    @Column(length = 1000)
    private String decisionReason;

    private LocalDateTime executedAt;

    private LocalDateTime createdAt = LocalDateTime.now();
}
