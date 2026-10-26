package com.ecommerce.auth.repo;
import com.ecommerce.auth.model.CustomerOrderLedger;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.util.Optional;
public interface CustomerOrderLedgerRepository extends JpaRepository<CustomerOrderLedger,Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select l from CustomerOrderLedger l where l.orderId=:id")Optional<CustomerOrderLedger> lock(@Param("id")Long id);
    @Query("select coalesce(sum(l.merchandiseTotal),0) from CustomerOrderLedger l where l.customerId=:id and l.status='DELIVERED' and l.paymentStatus not in ('REFUNDED','CANCELLED')")BigDecimal deliveredSpend(@Param("id")Long id);
    @Query("select count(l) from CustomerOrderLedger l where l.customerId=:id and l.status='DELIVERED' and l.paymentStatus not in ('REFUNDED','CANCELLED')")long completedCount(@Param("id")Long id);
    boolean existsByCustomerId(Long customerId);
}
