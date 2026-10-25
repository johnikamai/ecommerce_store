package com.ecommerce.order.repo;
import com.ecommerce.order.model.GroupPayment;
import com.ecommerce.common.ServiceClient;
import org.springframework.stereotype.Component;
import java.util.*;
@Component
public class GroupPaymentRepository {
    private final ServiceClient http;public GroupPaymentRepository(ServiceClient http){this.http=http;}
    public GroupPayment save(GroupPayment payment){return http.post("http://payment-service/internal/group-payments",payment,GroupPayment.class);}
    public List<GroupPayment> findByGroupOrder_Id(Long id){return Arrays.asList(http.get("http://payment-service/internal/group-payments/group/"+id,GroupPayment[].class).orElse(new GroupPayment[0]));}
    public Optional<GroupPayment> findByGroupOrder_IdAndMember_Id(Long groupId,Long memberId){return http.get("http://payment-service/internal/group-payments/group/"+groupId+"/member/"+memberId,GroupPayment.class);}
}
