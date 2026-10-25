package com.ecommerce.product.repo;

import com.ecommerce.product.model.PriceHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface PriceHistoryRepository extends JpaRepository<PriceHistory, Long> {

    /** Last 90 price points for the graph (oldest first). */
    List<PriceHistory> findTop90ByProductIdOrderByChangedAtAsc(Long productId);

    /** Most-recent price record for a product (used to detect duplicate writes). */
    PriceHistory findTopByProductIdOrderByChangedAtDesc(Long productId);

    /**
     * Find wishlist items where the product's latest price is lower than the price
     * recorded at the time the item was added.
     * Returns Object[] rows: [customerId, productId, priceWhenAdded, currentPrice, productName].
     */
    @Query(value = """
        SELECT w.customer_id,
               w.product_id,
               ph_added.new_price,
               ph_cur.new_price,
               p.name
        FROM wishlist_items w
        JOIN products p ON p.id = w.product_id
        JOIN price_history ph_added ON ph_added.product_id = w.product_id
        JOIN price_history ph_cur ON ph_cur.product_id = w.product_id
        WHERE ph_added.changed_at = (
                SELECT MIN(ph2.changed_at)
                FROM price_history ph2
                WHERE ph2.product_id = w.product_id
                  AND ph2.changed_at >= w.added_at
              )
          AND ph_cur.changed_at = (
                SELECT MAX(ph3.changed_at)
                FROM price_history ph3
                WHERE ph3.product_id = w.product_id
              )
          AND ph_cur.new_price < ph_added.new_price
    """, nativeQuery = true)
    List<Object[]> findWishlistPriceDrops();

    /** Prevents duplicate inserts when the same price is set twice within a minute. */
    @Query("""
        select count(ph) > 0
        from PriceHistory ph
        where ph.productId = :pid
          and ph.newPrice  = :price
          and ph.changedAt > :since
    """)
    boolean existsRecent(@Param("pid")   Long pid,
                         @Param("price") BigDecimal price,
                         @Param("since") LocalDateTime since);
}

