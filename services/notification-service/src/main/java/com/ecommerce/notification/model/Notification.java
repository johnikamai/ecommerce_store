package com.ecommerce.notification.model;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "notifications")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @com.fasterxml.jackson.annotation.JsonIgnore @Column(name="customer_id",nullable=false) private Long customerId;
    @Transient private Customer customer;
    public Customer getCustomer(){if(customer==null&&customerId!=null){customer=new Customer();customer.setId(customerId);}return customer;}
    public void setCustomer(Customer value){customer=value;customerId=value==null?null:value.getId();}

    private String message;

    private String type; // e.g. ORDER, PAYMENT, SHIPPING, DELIVERY, RESTOCK, OFFER

    private Boolean isRead = false;

    private LocalDateTime sentAt = LocalDateTime.now();
}