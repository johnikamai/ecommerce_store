package com.ecommerce.ecommerce_system.repository;

import com.ecommerce.ecommerce_system.model.ReturnRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReturnRequestRepository extends JpaRepository<ReturnRequest, Long> {

    // Returns requested by a specific customer (to show in their profile).
    List<ReturnRequest> findByCustomerId(Long customerId);

    // Returns currently awaiting admin action.
    List<ReturnRequest> findByStatus(com.ecommerce.ecommerce_system.model.ReturnStatus status);
}
