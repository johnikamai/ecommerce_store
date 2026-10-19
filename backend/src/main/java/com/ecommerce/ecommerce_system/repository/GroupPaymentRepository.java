package com.ecommerce.ecommerce_system.repository;

import com.ecommerce.ecommerce_system.model.GroupPayment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GroupPaymentRepository extends JpaRepository<GroupPayment, Long> {

    List<GroupPayment> findByGroupOrder_Id(Long groupOrderId);

    Optional<GroupPayment> findByGroupOrder_IdAndMember_Id(Long groupOrderId, Long memberId);
}
