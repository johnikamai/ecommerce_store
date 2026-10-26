package com.ecommerce.product.repo;

import com.ecommerce.product.model.ApprovalRequest;
import com.ecommerce.product.model.ApprovalStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequest, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select a from ApprovalRequest a where a.id=:id")
    java.util.Optional<ApprovalRequest> lock(@org.springframework.data.repository.query.Param("id")Long id);

    List<ApprovalRequest> findByStatusOrderByCreatedAtDesc(ApprovalStatus status);

    List<ApprovalRequest> findAllByOrderByCreatedAtDesc();

    List<ApprovalRequest> findByRequestedByUserIdOrderByCreatedAtDesc(Long userId);

    long countByStatus(ApprovalStatus status);
}
