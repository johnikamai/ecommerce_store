package com.ecommerce.auth.repo;

import com.ecommerce.auth.model.StaffNotification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface StaffNotificationRepository extends JpaRepository<StaffNotification, Long> {

    List<StaffNotification> findTop100ByUserIdOrderBySentAtDesc(Long userId);

    long countByUserIdAndIsReadFalse(Long userId);

    @Modifying
    @Query("update StaffNotification n set n.isRead = true where n.user.id = :userId and n.isRead = false")
    int markAllRead(@Param("userId") Long userId);
}
