package com.ecommerce.order.repo;

import com.ecommerce.order.model.ReturnRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReturnRequestRepository extends JpaRepository<ReturnRequest, Long> {

    // Returns requested by a specific customer (to show in their profile).
    List<ReturnRequest> findByCustomerId(Long customerId);

    // Returns currently awaiting admin action.
    List<ReturnRequest> findByStatus(com.ecommerce.order.model.ReturnStatus status);

    long deleteByCustomerId(Long customerId);
}
