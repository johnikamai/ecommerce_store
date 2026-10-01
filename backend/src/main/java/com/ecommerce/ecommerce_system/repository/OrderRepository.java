package com.ecommerce.ecommerce_system.repository;

import com.ecommerce.ecommerce_system.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByCustomerId(Long customerId);
}
