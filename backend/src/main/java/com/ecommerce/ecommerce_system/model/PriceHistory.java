package com.ecommerce.ecommerce_system.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Immutable audit record of every price change for a product.
 * Written once when a price is updated; never mutated afterwards.
 * Used by:
 *   - GET /api/products/{id}/price-history  -> price history graph on ProductDetail
 *   - StockAlertService (scheduled)          -> price-drop wishlist notifications
 */
@Entity
@Table(name = "price_history", indexes = {
    @Index(name = "idx_price_history_product", columnList = "product_id"),
    @Index(name = "idx_price_history_changed_at", columnList = "changed_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PriceHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    /** Price before the change (null for the very first record). */
    @Column(name = "old_price", precision = 12, scale = 2)
    private BigDecimal oldPrice;

    /** Price after the change. */
    @Column(name = "new_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal newPrice;

    /** Wall-clock time the change was recorded. */
    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt = LocalDateTime.now();

    /** Free-form label, e.g. "Sale", "Price update", "Initial price". */
    @Column(length = 120)
    private String reason;

    public PriceHistory(Long productId, BigDecimal oldPrice, BigDecimal newPrice, String reason) {
        this.productId = productId;
        this.oldPrice  = oldPrice;
        this.newPrice  = newPrice;
        this.changedAt = LocalDateTime.now();
        this.reason    = reason;
    }
}
