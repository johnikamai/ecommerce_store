package com.ecommerce.product.model;
import lombok.Data;
/** Customer profile transported over HTTP, never mapped into the product schema. */
@Data
public class Customer {
    private Long id;private String name;private String email;private String phone;private String shippingAddress;
    private Integer loyaltyPoints;private String tier;private String referralCode;private Boolean referralRewarded;
}
