package com.ecommerce.payment.repo;
import com.ecommerce.payment.model.GroupPayment;
import org.springframework.data.jpa.repository.*;
import java.util.*;
public interface GroupPaymentRepository extends JpaRepository<GroupPayment,Long> {
    List<GroupPayment> findByGroupOrderId(Long groupId);
    Optional<GroupPayment> findByGroupOrderIdAndMemberId(Long groupId,Long memberId);
}
