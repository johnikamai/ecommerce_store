package com.ecommerce.recommendation.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;



@JsonIgnoreProperties(ignoreUnknown=true)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Customer {

    
    
    private Long id;

    
    
    private long version;

    private String name;

    
    private String email;

    private String phone;

    private String shippingAddress;

    private Integer loyaltyPoints = 0;

    private String tier = "BRONZE";

    // Unique invite code this customer shares with friends.
    
    private String referralCode;

    // The customer who referred this one (via their referral code).
    // Self-referencing relationship: a customer can only come from one referrer.
    // Ignored during JSON serialization to avoid infinite recursion.
    
    
    private Customer referredBy;

    // Has the referrer already been rewarded for THIS customer's first order?
    // Prevents awarding the bonus more than once.
    private Boolean referralRewarded = false;
}
