package com.ecommerce.ecommerce_system.repository;

import com.ecommerce.ecommerce_system.model.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface ReviewRepository extends JpaRepository<Review, Long> {
    List<Review> findByProductId(Long productId);

    List<Review> findByCustomerId(Long customerId);

    List<Review> findByCustomerIdAndProductId(Long customerId, Long productId);

    /** Per-product aggregates (productId, reviewCount, averageRating) for the storefront catalog. */
    @Query("select r.product.id as pid, count(r) as cnt, avg(r.rating) as avgRating from Review r group by r.product.id")
    List<Object[]> aggregateByProduct();
}
