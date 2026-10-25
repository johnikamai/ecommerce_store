package com.ecommerce.payment.service;
import com.ecommerce.payment.model.Customer;
import com.ecommerce.common.outbox.OutboxPublisher;
import org.springframework.stereotype.Service;
import java.util.*;
@Service
public class NotificationService {
    private final OutboxPublisher outbox;
    public NotificationService(OutboxPublisher outbox){this.outbox=outbox;}
    public void broadcastOffer(String code,String description,List<Customer> customers){outbox.publish(UUID.randomUUID().toString(),"http://notification-service/internal/events","COUPON_OFFER",Map.of("code",code,"description",description,"customerIds",customers.stream().map(Customer::getId).toList()));}
}
