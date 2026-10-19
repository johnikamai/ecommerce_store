package com.ecommerce.ecommerce_system.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** One product line in a group cart, attributed to the member who added it. */
@Entity
@Table(name = "group_cart_items",
        indexes = @Index(name = "idx_group_cart_group", columnList = "group_order_id"))
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GroupCartItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_order_id", nullable = false)
    @JsonIgnore
    private GroupOrder groupOrder;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "added_by_id", nullable = false)
    private Customer addedBy;

    @Column(nullable = false)
    private Integer quantity;

    /** Price when the item was added; revalidated against the live price at checkout. */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    private LocalDateTime addedAt = LocalDateTime.now();
    private LocalDateTime updatedAt = LocalDateTime.now();

    /** Current line total. Live quantity x snapshot unit price, like OrderItem. */
    @Transient
    public BigDecimal getLineTotal() {
        if (unitPrice == null || quantity == null) return BigDecimal.ZERO;
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
