package com.ecommerce.product.service;
import com.ecommerce.product.model.User;
import com.ecommerce.common.outbox.OutboxPublisher;
import org.springframework.stereotype.Service;
import java.util.*;
@Service
public class StaffNotificationService {
    private final OutboxPublisher outbox;
    public StaffNotificationService(OutboxPublisher outbox){this.outbox=outbox;}
    public void notify(User user,String message,String type){outbox.publish(UUID.randomUUID().toString(),"http://auth-service/internal/staff/events","STAFF_NOTIFICATION",Map.of("userId",user.getId(),"message",message,"type",type));}
}
