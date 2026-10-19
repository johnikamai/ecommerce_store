package com.ecommerce.ecommerce_system.repository;

import com.ecommerce.ecommerce_system.model.GroupOrder;
import com.ecommerce.ecommerce_system.model.GroupOrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface GroupOrderRepository extends JpaRepository<GroupOrder, Long> {

    Optional<GroupOrder> findByInviteCode(String inviteCode);

    List<GroupOrder> findByHostId(Long hostId);

    @Query("select distinct g from GroupOrder g join g.members m where m.customer.id = :customerId order by g.createdAt desc")
    List<GroupOrder> findByMemberCustomerId(@Param("customerId") Long customerId);

    @Query("select g from GroupOrder g where (:status is null or g.status = :status) order by g.createdAt desc")
    List<GroupOrder> findAllFiltered(@Param("status") GroupOrderStatus status);

    long countByStatus(GroupOrderStatus status);
}
