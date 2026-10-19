package com.ecommerce.ecommerce_system.repository;

import com.ecommerce.ecommerce_system.model.StaffActivityLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StaffActivityLogRepository extends JpaRepository<StaffActivityLog, Long> {

    List<StaffActivityLog> findTop200ByOrderByCreatedAtDesc();

    List<StaffActivityLog> findByActorUsernameOrderByCreatedAtDesc(String actorUsername);

    List<StaffActivityLog> findTop100ByTargetTypeAndTargetIdOrderByCreatedAtDesc(String targetType, Long targetId);
}
