package com.ecommerce.ecommerce_system.repository;

import com.ecommerce.ecommerce_system.model.GroupBillSplit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GroupBillSplitRepository extends JpaRepository<GroupBillSplit, Long> {

    Optional<GroupBillSplit> findByGroupOrder_Id(Long groupOrderId);
}
