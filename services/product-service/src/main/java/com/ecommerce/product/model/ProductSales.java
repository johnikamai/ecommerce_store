package com.ecommerce.product.model;
import jakarta.persistence.*;
import lombok.Data;
@Entity @Table(name="product_sales") @Data
public class ProductSales { @Id private Long productId;private Long unitsSold=0L; }
