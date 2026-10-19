package com.ecommerce.ecommerce_system.repository;

import com.ecommerce.ecommerce_system.model.GroupInvitation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GroupInvitationRepository extends JpaRepository<GroupInvitation, Long> {

    Optional<GroupInvitation> findByCode(String code);

    List<GroupInvitation> findByGroupOrder_Id(Long groupOrderId);

    boolean existsByCodeAndStatus(String code, String status);
}
