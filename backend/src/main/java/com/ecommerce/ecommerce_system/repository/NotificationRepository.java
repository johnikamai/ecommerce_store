package com.ecommerce.ecommerce_system.repository;

import com.ecommerce.ecommerce_system.model.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByCustomerIdOrderBySentAtDesc(Long customerId);
}