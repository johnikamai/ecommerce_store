package com.ecommerce.ecommerce_system.repository;

import com.ecommerce.ecommerce_system.model.GroupOrderMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GroupOrderMemberRepository extends JpaRepository<GroupOrderMember, Long> {

    List<GroupOrderMember> findByGroupOrder_IdOrderByJoinedAtAsc(Long groupOrderId);

    Optional<GroupOrderMember> findByGroupOrder_IdAndCustomer_Id(Long groupOrderId, Long customerId);
}
