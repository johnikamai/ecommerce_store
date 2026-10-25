package com.ecommerce.order.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Entity
@Table(name = "wishlist_items")
@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Wishlist {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @com.fasterxml.jackson.annotation.JsonIgnore
    @Column(name="customer_id",nullable=false) private Long customerId;
    @Transient private Customer customer;
    public Customer getCustomer(){if(customer==null&&customerId!=null){customer=new Customer();customer.setId(customerId);}return customer;}
    public void setCustomer(Customer value){customer=value;customerId=value==null?null:value.getId();}

    @com.fasterxml.jackson.annotation.JsonIgnore
    @Column(name="product_id",nullable=false) private Long productId;
    @Transient private Product product;
    public Product getProduct(){if(product==null&&productId!=null){product=new Product();product.setId(productId);}return product;}
    public void setProduct(Product value){product=value;productId=value==null?null:value.getId();}
}