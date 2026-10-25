package com.ecommerce.order.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;



@JsonIgnoreProperties(ignoreUnknown=true)
@Data
@NoArgsConstructor
public class Coupon {

    
    
    private Long id;

    
    private String code;

    private String description;

    
    
    private CouponType discountType = CouponType.PERCENT;

    // Percent value (e.g. 15 for 15%) or fixed rupee amount, depending on discountType.
    
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
    
    
    private long version;

    private int timesUsed = 0;

    public boolean isExpired() {
        return expiryDate != null && expiryDate.isBefore(LocalDate.now());
    }

    public boolean isRedeemable() {
        return Boolean.TRUE.equals(active) && !isExpired() && (maxUses == null || timesUsed < maxUses);
    }
}