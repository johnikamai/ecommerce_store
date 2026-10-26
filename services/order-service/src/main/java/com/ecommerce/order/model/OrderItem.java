package com.ecommerce.order.model;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "order_items")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    @JsonBackReference
    private Order order;

    @com.fasterxml.jackson.annotation.JsonIgnore
    @Column(name="product_id",nullable=false) private Long productId;
    @Transient private Product product;
    public Product getProduct(){if(product==null&&productId!=null){product=new Product();product.setId(productId);}return product;}
    public void setProduct(Product value){product=value;productId=value==null?null:value.getId();}

    private Integer quantity;

    private BigDecimal unitPrice;

    // Sub-total captured at order time (unitPrice x quantity). Stored rather than
    // recomputed so a line's amount can never drift if the product price changes.
    private BigDecimal lineTotal;
}
