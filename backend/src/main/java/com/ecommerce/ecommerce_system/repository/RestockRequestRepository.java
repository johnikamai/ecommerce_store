package com.ecommerce.ecommerce_system.repository;

import com.ecommerce.ecommerce_system.model.RestockRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RestockRequestRepository extends JpaRepository<RestockRequest, Long> {

    // All subscriptions for a given product that have NOT been notified yet.
    // Used when stock is restored so we can alert everyone who asked.
    List<RestockRequest> findByProductIdAndNotifiedFalse(Long productId);

    // All subscriptions belonging to one customer (to list in their profile).
    List<RestockRequest> findByCustomerId(Long customerId);

    // Prevent duplicate subscriptions: the same customer + product only once.
    boolean existsByCustomerIdAndProductId(Long customerId, Long productId);

    long deleteByCustomerId(Long customerId);
}
