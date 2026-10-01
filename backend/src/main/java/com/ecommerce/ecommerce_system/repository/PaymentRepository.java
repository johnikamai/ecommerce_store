package com.ecommerce.ecommerce_system.repository;

import com.ecommerce.ecommerce_system.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByOrderId(Long orderId);
    List<Payment> findByOrder_CustomerId(Long customerId);
}