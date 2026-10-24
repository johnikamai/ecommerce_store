package com.ecommerce.ecommerce_system.repository;

import com.ecommerce.ecommerce_system.model.PriceHistory;
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
    @Query("""
        select w.customer.id,
               w.product.id,
               ph_added.newPrice,
               ph_cur.newPrice,
               w.product.name
        from Wishlist w
        join PriceHistory ph_added
             on ph_added.productId = w.product.id
        join PriceHistory ph_cur
             on ph_cur.productId = w.product.id
        where ph_added.changedAt = (
                select min(ph2.changedAt)
                from PriceHistory ph2
                where ph2.productId = w.product.id
                  and ph2.changedAt >= w.addedAt
              )
          and ph_cur.changedAt = (
                select max(ph3.changedAt)
                from PriceHistory ph3
                where ph3.productId = w.product.id
              )
          and ph_cur.newPrice < ph_added.newPrice
    """)
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
