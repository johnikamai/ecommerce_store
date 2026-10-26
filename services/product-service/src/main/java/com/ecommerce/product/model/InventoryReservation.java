package com.ecommerce.product.model;
import jakarta.persistence.*;
import lombok.Data;
@Entity @Table(name="inventory_reservations") @Data
public class InventoryReservation {
    @Id @Column(length=128)private String id;
    @Column(nullable=false)private Long customerId;
    @Column(nullable=false,length=20)private String state;
    @Column(nullable=false,length=64)private String requestFingerprint;
    @Column(nullable=false,columnDefinition="longtext")private String payload;
    @Version private long version;
}
