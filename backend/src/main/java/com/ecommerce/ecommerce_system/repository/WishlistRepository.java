package com.ecommerce.ecommerce_system.repository;

import com.ecommerce.ecommerce_system.model.Wishlist;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface WishlistRepository extends JpaRepository<Wishlist, Long> {
    List<Wishlist> findByCustomerId(Long customerId);

    List<Wishlist> findByProductId(Long productId);

    Optional<Wishlist> findByCustomerIdAndProductId(Long customerId, Long productId);

    long deleteByCustomerId(Long customerId);
}
