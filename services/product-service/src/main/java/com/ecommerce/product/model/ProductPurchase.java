package com.ecommerce.product.model;
import jakarta.persistence.*;
import lombok.Data;
@Entity @Table(name="product_purchases") @Data
public class ProductPurchase { @Id @Column(length=128)private String id;private Long customerId;private Long productId;private Long quantity=0L; }
