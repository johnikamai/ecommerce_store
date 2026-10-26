package com.ecommerce.order.repo;

import com.ecommerce.order.model.GroupOrderMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GroupOrderMemberRepository extends JpaRepository<GroupOrderMember, Long> {

    List<GroupOrderMember> findByGroupOrder_IdOrderByJoinedAtAsc(Long groupOrderId);

    Optional<GroupOrderMember> findByGroupOrder_IdAndCustomerId(Long groupOrderId, Long customerId);
}
