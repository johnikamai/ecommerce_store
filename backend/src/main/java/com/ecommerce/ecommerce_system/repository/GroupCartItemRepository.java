package com.ecommerce.ecommerce_system.repository;

import com.ecommerce.ecommerce_system.model.GroupCartItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GroupCartItemRepository extends JpaRepository<GroupCartItem, Long> {

    List<GroupCartItem> findByGroupOrder_IdOrderByAddedAtAsc(Long groupOrderId);

    List<GroupCartItem> findByGroupOrder_IdAndAddedBy_Id(Long groupOrderId, Long customerId);
}
