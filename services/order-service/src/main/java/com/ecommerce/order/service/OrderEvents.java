package com.ecommerce.order.service;
import com.ecommerce.order.model.*;
import com.ecommerce.common.outbox.OutboxPublisher;
import org.springframework.stereotype.Service;
import java.util.*;
@Service
public class OrderEvents {
    private final OutboxPublisher outbox;
    public OrderEvents(OutboxPublisher outbox){this.outbox=outbox;}
    public void updated(Order order){
        var payload=new LinkedHashMap<String,Object>();payload.put("orderId",order.getId());payload.put("customerId",order.getCustomerId());payload.put("status",order.getStatus().name());payload.put("paymentStatus",order.getPaymentStatus().name());payload.put("merchandiseTotal",order.getMerchandiseTotalForScoring());payload.put("totalAmount",order.getTotalAmount());payload.put("rewardsAwarded",Boolean.TRUE.equals(order.getRewardsAwarded()));payload.put("version",order.getVersion());
        String key="order:"+order.getId()+":"+order.getVersion();
        outbox.publish("auth:"+key,"http://auth-service/internal/customer-order-events","ORDER_UPDATED",payload);
        outbox.publish("payment:"+key,"http://payment-service/internal/events","ORDER_STATUS",payload);
    }
}
