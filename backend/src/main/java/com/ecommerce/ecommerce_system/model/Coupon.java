package com.ecommerce.ecommerce_system.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "coupons")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Data
@NoArgsConstructor
public class Coupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String code;

    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CouponType discountType = CouponType.PERCENT;

    // Percent value (e.g. 15 for 15%) or fixed rupee amount, depending on discountType.
    @Column(nullable = false)
    private BigDecimal discountValue;

    // Orders below this subtotal cannot use the coupon. Null = no minimum.
    private BigDecimal minimumOrderAmount;

    // Null = no expiry.
    private LocalDate expiryDate;

    private Boolean active = true;

    // Null = unlimited uses.
    private Integer maxUses;

    /**
     * Incremented once per redemption. The version makes that increment safe.
     *
     * Without it, two shoppers redeeming the last remaining use at the same moment
     * both read timesUsed, both pass the limit check, both write the same value,
     * and the coupon is spent twice while only one use is recorded. JPA then fails
     * the second commit instead, so the customer sees an error rather than the
     * store quietly losing a discount it had promised.
     */
    @Version
    @Column(nullable = false)
    private long version;

    private int timesUsed = 0;

    public boolean isExpired() {
        return expiryDate != null && expiryDate.isBefore(LocalDate.now());
    }

    public boolean isRedeemable() {
        return Boolean.TRUE.equals(active) && !isExpired() && (maxUses == null || timesUsed < maxUses);
    }
}