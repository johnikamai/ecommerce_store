package com.ecommerce.ecommerce_system.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "return_requests")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReturnRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(nullable = false, columnDefinition = "bigint default 0")
    private long version;

    // Which line item (product + quantity) is being returned.
    // Linking to OrderItem (not just Order) means we know exactly what
    // to restore to stock and how much to refund.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_item_id", nullable = false)
    private OrderItem orderItem;

    // Who requested the return.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    private String reason;

    @Enumerated(EnumType.STRING)
    private ReturnStatus status = ReturnStatus.REQUESTED;

    private LocalDateTime requestDate = LocalDateTime.now();

    private LocalDateTime processedDate;
}
