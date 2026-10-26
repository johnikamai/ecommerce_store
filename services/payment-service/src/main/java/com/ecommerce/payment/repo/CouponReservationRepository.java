package com.ecommerce.payment.repo;
import com.ecommerce.payment.model.CouponReservation;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;
public interface CouponReservationRepository extends JpaRepository<CouponReservation,String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select r from CouponReservation r where r.id=:id") Optional<CouponReservation> lock(@Param("id")String id);
}
