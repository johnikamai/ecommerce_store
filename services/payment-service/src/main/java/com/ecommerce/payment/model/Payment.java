package com.ecommerce.payment.model;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payments")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(nullable = false, columnDefinition = "bigint default 0")
    private long version;

    @Column(name="order_id",nullable=false,unique=true) private Long orderId;
    private Long customerId;
    @Convert(converter=com.ecommerce.payment.config.OrderSnapshotConverter.class)
    @Column(name="order_snapshot",columnDefinition="longtext") private Order order;
    public void setOrder(Order value){order=value;orderId=value==null?null:value.getId();customerId=value==null||value.getCustomer()==null?null:value.getCustomer().getId();}

    @Enumerated(EnumType.STRING)
    private PaymentMode paymentMode;

    @Enumerated(EnumType.STRING)
    private PaymentStatus paymentStatus = PaymentStatus.PENDING;

    private BigDecimal amount;

    // Gateway reference: UPI txn id, card auth code, or the cash receipt number.
    // Null for Cash on Delivery until the money is actually collected.
    private String transactionRef;

    private LocalDateTime transactionDate = LocalDateTime.now();

    // Who last moved the status, and when. Keeps the ledger auditable.
    private LocalDateTime statusUpdatedAt = LocalDateTime.now();

    private String statusUpdatedBy;
}