package com.ecommerce.auth.repo;

import com.ecommerce.auth.model.StaffTask;
import com.ecommerce.auth.model.TaskStatus;
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
