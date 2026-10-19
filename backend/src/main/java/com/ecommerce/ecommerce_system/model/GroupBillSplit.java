package com.ecommerce.ecommerce_system.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * The computed split for a group bill at a given version.
 *
 * All money is calculated server-side in integer paise (see
 * {@code GroupSplitCalculator}) and only the human-readable snapshot is stored
 * here, so the UI never recomputes shares and can never disagree with billing.
 */
@Entity
@Table(name = "group_bill_splits",
        indexes = @Index(name = "idx_group_bill_group", columnList = "group_order_id", unique = true))
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GroupBillSplit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_order_id", nullable = false)
    @JsonIgnore
    private GroupOrder groupOrder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GroupSplitMethod method;

    /** Must match GroupOrder.splitVersion; bumping it invalidates confirmations. */
    @Column(nullable = false)
    private Integer version;

    /** Sum of all member shares (equals the payable total). */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    /** Sum of member merchandise before shipping/tax/discounts. */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal merchandiseTotal;

    /** Shipping component of the combined order. */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal shipping;

    /** Tax component of the combined order. */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal tax;

    /** Coupon/bundle/loyalty discount total, allocated pro-rata in ITEM_BASED. */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal discountTotal;

    /** Short human explanation of how amounts were derived (shown in the UI). */
    @Column(length = 500)
    private String breakdown;

    private LocalDateTime computedAt = LocalDateTime.now();
}
