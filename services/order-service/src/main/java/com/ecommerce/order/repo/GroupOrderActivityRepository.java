package com.ecommerce.order.repo;

import com.ecommerce.order.model.GroupOrderActivity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GroupOrderActivityRepository extends JpaRepository<GroupOrderActivity, Long> {

    List<GroupOrderActivity> findTop50ByGroupOrder_IdOrderByCreatedAtDesc(Long groupOrderId);
}
