package com.ecommerce.payment.model;
import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
@Entity @Table(name="coupon_reservations") @Data
public class CouponReservation {
    @Id @Column(length=128)private String id;
    @Column(nullable=false)private Long couponId;
    @Column(nullable=false,length=20)private String state;
    @Column(nullable=false,length=64)private String requestFingerprint;
    @Column(nullable=false)private BigDecimal discount;
}
