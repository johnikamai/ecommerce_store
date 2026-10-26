package com.ecommerce.payment.repo;

import com.ecommerce.payment.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select p from Payment p where p.id=:id")
    Optional<Payment> lock(@org.springframework.data.repository.query.Param("id")Long id);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select p from Payment p where p.orderId=:orderId")
    Optional<Payment> lockOrder(@org.springframework.data.repository.query.Param("orderId")Long orderId);
    Optional<Payment> findByOrderId(Long orderId);
    List<Payment> findByCustomerId(Long customerId);
}