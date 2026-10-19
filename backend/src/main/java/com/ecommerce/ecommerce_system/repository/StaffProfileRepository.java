package com.ecommerce.ecommerce_system.repository;

import com.ecommerce.ecommerce_system.model.Department;
import com.ecommerce.ecommerce_system.model.StaffProfile;
import com.ecommerce.ecommerce_system.model.StaffStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StaffProfileRepository extends JpaRepository<StaffProfile, Long> {

    Optional<StaffProfile> findByUserId(Long userId);

    Optional<StaffProfile> findByUserUsername(String username);

    List<StaffProfile> findByStatusOrderByCreatedAtDesc(StaffStatus status);

    List<StaffProfile> findAllByOrderByCreatedAtDesc();

    List<StaffProfile> findByDepartmentOrderByCreatedAtDesc(Department department);

    List<StaffProfile> findByFullNameContainingIgnoreCaseOrUserUsernameContainingIgnoreCaseOrUserEmailContainingIgnoreCase(
            String fullName, String username, String email);

    long countByStatus(StaffStatus status);

    long countByDepartment(Department department);

    boolean existsByUserId(Long userId);
}
