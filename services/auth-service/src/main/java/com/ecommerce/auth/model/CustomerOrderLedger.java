package com.ecommerce.auth.model;
import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
@Entity @Table(name="customer_order_ledger") @Data
public class CustomerOrderLedger {
    @Id private Long orderId;private Long customerId;
    private BigDecimal merchandiseTotal;private BigDecimal totalAmount;
    private String status;private String paymentStatus;private boolean rewarded;
    private long sourceVersion=-1;
}
