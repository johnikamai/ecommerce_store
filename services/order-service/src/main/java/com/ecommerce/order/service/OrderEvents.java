package com.ecommerce.order.service;
import com.ecommerce.order.model.*;
import com.ecommerce.common.outbox.OutboxPublisher;
import org.springframework.stereotype.Service;
import java.util.*;
@Service
public class OrderEvents {
    private final OutboxPublisher outbox;
    private final com.ecommerce.order.repo.GroupOrderRepository groups;
    public OrderEvents(OutboxPublisher outbox,com.ecommerce.order.repo.GroupOrderRepository groups){this.outbox=outbox;this.groups=groups;}
    public void updated(Order order){
        var payload=new LinkedHashMap<String,Object>();payload.put("orderId",order.getId());payload.put("customerId",order.getCustomerId());payload.put("status",order.getStatus().name());payload.put("paymentStatus",order.getPaymentStatus().name());payload.put("merchandiseTotal",order.getMerchandiseTotalForScoring());payload.put("totalAmount",order.getTotalAmount());payload.put("rewardsAwarded",Boolean.TRUE.equals(order.getRewardsAwarded()));payload.put("version",order.getVersion());
        String key="order:"+order.getId()+":"+order.getVersion();
        outbox.publish("auth:"+key,"http://auth-service/internal/customer-order-events","ORDER_UPDATED",payload);
        outbox.publish("payment:"+key,"http://payment-service/internal/events","ORDER_STATUS",payload);
        for (var group : groups.findByPlacedOrder_Id(order.getId())) for (var member : group.getMembers()) {
            if (member.getCustomerId().equals(group.getHostId())) continue;
            String message="Group order #"+order.getId()+" is "+order.getStatus().name()+"; payment "+order.getPaymentStatus().name()+".";
            outbox.publish("group:"+key+":"+member.getCustomerId(),"http://notification-service/internal/events","NOTIFY",
                Map.of("customerId",member.getCustomerId(),"type","GROUP","message",message,"subject","Group order update","html",message));
        }
    }
}
