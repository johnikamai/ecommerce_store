package com.ecommerce.ecommerce_system.repository;

import com.ecommerce.ecommerce_system.model.AdminAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AdminAlertRepository extends JpaRepository<AdminAlert, Long> {

    List<AdminAlert> findTop50ByOrderByCreatedAtDesc();

    @Query("select count(a) from AdminAlert a where a.isRead = false")
    long countUnread();

    /** True when an unread alert for this product+type already exists, so a
     *  product sitting below its reorder level does not spam the list on every sale. */
    @Query("select count(a) > 0 from AdminAlert a "
            + "where a.productId = :productId and a.type = :type and a.isRead = false")
    boolean existsUnread(@Param("productId") Long productId, @Param("type") String type);
}