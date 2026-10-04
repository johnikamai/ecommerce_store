package com.ecommerce.ecommerce_system.model;
import com.fasterxml.jackson.annotation.JsonIgnore;
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

    // Amount knocked off by the multi-item bundle tier. Kept separate from
    // discountAmount so the cart and the invoice can show *why* the order was
    // cheaper; the two are applied one after the other and both stack.
    private BigDecimal bundleDiscountAmount = BigDecimal.ZERO;

    // Delivery snapshot captured at checkout (address book entry or manual entry).
    private String shippingAddress;

    // Payment method chosen at checkout: CASH (COD), UPI or CARD.
    private String paymentMethod;

    // Current payment state for this order (null for legacy orders).
    @Enumerated(EnumType.STRING)
    private PaymentStatus paymentStatus;

    // ---- Order tracking ----
    //
    // These are persisted here but deliberately NOT serialized onto the order
    // JSON. GET /api/orders/{id}/tracking (OrderService.trackingFor) is the
    // single source of truth for tracking, and it returns a superset of these
    // fields plus the derived milestone rail and scan history.
    //
    // Exposing them in both places let the two drift apart, and embedding the
    // scan history in every order response cost an extra query per order on
    // list endpoints that never display it.

    // Courier reference, issued when the parcel is handed over. Null until then.
    @JsonIgnore
    private String trackingNumber;

    // Carrier name, e.g. "Delhivery". Null for digital goods or unfulfilled orders.
    @JsonIgnore
    private String carrier;

    // Promised delivery date shown to the customer, set when the order ships.
    @JsonIgnore
    private LocalDateTime expectedDelivery;

    // When the parcel actually left the warehouse and when it arrived.
    @JsonIgnore
    private LocalDateTime shippedAt;
    @JsonIgnore
    private LocalDateTime deliveredAt;

    // Append-only scan history powering the customer-facing tracker. Read only
    // through the tracking endpoint, which shapes it for display.
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    private List<OrderStatusEvent> statusEvents = new ArrayList<>();

    public void addStatusEvent(OrderStatusEvent event) {
        event.setOrder(this);
        statusEvents.add(event);
    }

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
