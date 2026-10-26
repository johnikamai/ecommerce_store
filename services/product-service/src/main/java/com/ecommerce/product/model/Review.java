package com.ecommerce.product.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "reviews")
@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @com.fasterxml.jackson.annotation.JsonIgnore
    @Column(name="customer_id",nullable=false)
    private Long customerId;
    @Transient
    private Customer customer;
    public Customer getCustomer() {
        if(customer==null && customerId!=null){customer=new Customer();customer.setId(customerId);}
        return customer;
    }
    public void setCustomer(Customer customer){this.customer=customer;this.customerId=customer==null?null:customer.getId();}

    @Column(nullable = false)
    private Integer rating; // 1 to 5

    private String comment;

    private LocalDateTime createdAt = LocalDateTime.now();
}
