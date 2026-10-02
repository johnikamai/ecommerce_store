package com.ecommerce.ecommerce_system.repository;

import com.ecommerce.ecommerce_system.model.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByCustomerIdOrderBySentAtDesc(Long customerId);

    // The bell panel only ever renders the newest handful, so the list is bounded
    // instead of returning a customer's entire history on every page load.
    List<Notification> findTop50ByCustomerIdOrderBySentAtDesc(Long customerId);

    long countByCustomerIdAndIsReadFalse(Long customerId);

    // Used when an admin deletes a customer, so the non-nullable FK does not
    // surface as a constraint violation.
    long deleteByCustomerId(Long customerId);

    // Single statement instead of saving each row in a loop.
    @Modifying
    @Query("update Notification n set n.isRead = true where n.customer.id = :customerId and n.isRead = false")
    int markAllRead(@Param("customerId") Long customerId);
}