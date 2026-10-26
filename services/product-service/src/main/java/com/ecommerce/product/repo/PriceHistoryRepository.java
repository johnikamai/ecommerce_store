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
