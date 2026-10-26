package com.ecommerce.auth.model;
import jakarta.persistence.*;
@Entity @Table(name="service_inbox")
public class InboxReceipt {
    @Id @Column(length=128) public String id;
}
