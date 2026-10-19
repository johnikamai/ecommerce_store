package com.ecommerce.ecommerce_system.repository;

import com.ecommerce.ecommerce_system.model.StaffTask;
import com.ecommerce.ecommerce_system.model.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface StaffTaskRepository extends JpaRepository<StaffTask, Long> {

    List<StaffTask> findByAssignedToIdOrderByCreatedAtDesc(Long userId);

    List<StaffTask> findAllByOrderByCreatedAtDesc();

    List<StaffTask> findByStatusOrderByCreatedAtDesc(TaskStatus status);

    List<StaffTask> findByAssignedToIdAndStatusOrderByCreatedAtDesc(Long userId, TaskStatus status);

    long countByAssignedToId(Long userId);

    long countByAssignedToIdAndStatusIn(Long userId, Collection<TaskStatus> statuses);

    long countByStatus(TaskStatus status);

    List<StaffTask> findByAssignedToIdAndStatusIn(Long userId, Collection<TaskStatus> statuses);
}
