package com.ecommerce.order.repo;

import com.ecommerce.order.model.GroupCartItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GroupCartItemRepository extends JpaRepository<GroupCartItem, Long> {

    List<GroupCartItem> findByGroupOrder_IdOrderByAddedAtAsc(Long groupOrderId);

    List<GroupCartItem> findByGroupOrder_IdAndAddedById(Long groupOrderId, Long customerId);
}
