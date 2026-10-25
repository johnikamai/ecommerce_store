package com.ecommerce.product.model;
import jakarta.persistence.*;
import lombok.Data;
@Entity @Table(name="product_operations") @Data
public class ProductOperation {
    @Id @Column(length=128)private String id;
    @Column(nullable=false,length=64)private String requestFingerprint;
}
