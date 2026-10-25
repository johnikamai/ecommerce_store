package com.ecommerce.product.repo;
import com.ecommerce.product.model.InventoryReservation;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;
public interface InventoryReservationRepository extends JpaRepository<InventoryReservation,String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select r from InventoryReservation r where r.id=:id")Optional<InventoryReservation> lock(@Param("id")String id);
    List<InventoryReservation> findByStateNot(String state);
}
