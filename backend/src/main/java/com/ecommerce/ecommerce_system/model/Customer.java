package com.ecommerce.ecommerce_system.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Entity
@Table(name = "customers")
@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler", "referredBy" })
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(nullable = false, columnDefinition = "bigint default 0")
    private long version;

    private String name;

    @Column(unique = true)
    private String email;

    private String phone;

    private String shippingAddress;

    private Integer loyaltyPoints = 0;

    private String tier = "BRONZE";

    // Unique invite code this customer shares with friends.
    @Column(unique = true)
    private String referralCode;

    // The customer who referred this one (via their referral code).
    // Self-referencing relationship: a customer can only come from one referrer.
    // Ignored during JSON serialization to avoid infinite recursion.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "referred_by_id")
    private Customer referredBy;

    // Has the referrer already been rewarded for THIS customer's first order?
    // Prevents awarding the bonus more than once.
    private Boolean referralRewarded = false;
}
