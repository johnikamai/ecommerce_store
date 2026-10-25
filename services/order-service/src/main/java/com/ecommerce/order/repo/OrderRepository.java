package com.ecommerce.order.repo;

import com.ecommerce.order.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {
    java.util.Optional<Order> findByCheckoutKey(String checkoutKey);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select o from Order o where o.id=:id")
    java.util.Optional<Order> lock(@org.springframework.data.repository.query.Param("id")Long id);
    List<Order> findByCustomerId(Long customerId);
}
