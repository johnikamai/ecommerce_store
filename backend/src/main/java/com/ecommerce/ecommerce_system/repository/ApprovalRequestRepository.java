package com.ecommerce.ecommerce_system.repository;

import com.ecommerce.ecommerce_system.model.ApprovalRequest;
import com.ecommerce.ecommerce_system.model.ApprovalStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequest, Long> {

    List<ApprovalRequest> findByStatusOrderByCreatedAtDesc(ApprovalStatus status);

    List<ApprovalRequest> findAllByOrderByCreatedAtDesc();

    List<ApprovalRequest> findByRequestedByIdOrderByCreatedAtDesc(Long userId);

    long countByStatus(ApprovalStatus status);
}
