package com.ecommerce.order.model;
import jakarta.persistence.*;
import lombok.Data;
import java.time.Instant;
@Entity @Table(name="checkout_attempts") @Data
public class CheckoutAttempt {
    @Id @Column(length=128)private String id;
    @Column(nullable=false,length=20)private String kind="CHECKOUT";
    @Column(nullable=false,length=64)private String requestHash;
    @Column(nullable=false,columnDefinition="longtext")private String snapshot;
    private Long orderId;
    @Column(nullable=false,length=20)private String state="PENDING";
    @Column(length=500)private String error;
    private int attempts;
    private Instant nextAttemptAt=Instant.now();
    @Version private long version;
}
