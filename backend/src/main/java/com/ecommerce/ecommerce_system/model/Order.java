package com.ecommerce.ecommerce_system.model;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    private LocalDateTime orderDate = LocalDateTime.now();

    @Enumerated(EnumType.STRING)
    private OrderStatus status = OrderStatus.PLACED;

    private BigDecimal totalAmount = BigDecimal.ZERO;

    // Optional promo code applied at checkout (stored for the record).
    private String couponCode;

    // Amount knocked off by the coupon (stored so the invoice stays auditable).
    private BigDecimal discountAmount = BigDecimal.ZERO;

    // Delivery snapshot captured at checkout (address book entry or manual entry).
    private String shippingAddress;

    // Payment method chosen at checkout: CASH (COD), UPI or CARD.
    private String paymentMethod;

    // Current payment state for this order (null for legacy orders).
    @Enumerated(EnumType.STRING)
    private PaymentStatus paymentStatus;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference
    private List<OrderItem> orderItems = new ArrayList<>();

    // The statuses this order may legally move to right now. Computed, not stored,
    // so the admin console can offer exactly the options the backend accepts
    // instead of listing every status and failing on the invalid ones.
    @Transient
    public List<String> getAllowedNextStatuses() {
        return status == null ? List.of() : status.allowedNextOrdered().stream().map(Enum::name).toList();
    }
}
