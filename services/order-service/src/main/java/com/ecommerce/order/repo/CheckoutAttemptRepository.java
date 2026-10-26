package com.ecommerce.order.repo;
import com.ecommerce.order.model.CheckoutAttempt;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;
public interface CheckoutAttemptRepository extends JpaRepository<CheckoutAttempt,String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select a from CheckoutAttempt a where a.id=:id")Optional<CheckoutAttempt> lock(@Param("id")String id);
    List<CheckoutAttempt> findTop20ByStateAndNextAttemptAtLessThanEqualOrderById(String state,Instant now);
}
