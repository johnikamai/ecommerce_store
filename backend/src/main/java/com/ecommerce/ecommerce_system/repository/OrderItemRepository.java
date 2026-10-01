package com.ecommerce.ecommerce_system.repository;

import com.ecommerce.ecommerce_system.model.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
    List<OrderItem> findByOrderId(Long orderId);

    /** True if the customer has this product in an order that was not cancelled. */
    boolean existsByProductIdAndOrder_CustomerIdAndOrder_StatusNot(Long productId, Long customerId, com.ecommerce.ecommerce_system.model.OrderStatus status);

    /** Per-product units sold (productId, unitsSold) for popularity sorting. */
    @Query("select oi.product.id as pid, sum(oi.quantity) as qty from OrderItem oi group by oi.product.id")
    List<Object[]> unitsSoldByProduct();
}