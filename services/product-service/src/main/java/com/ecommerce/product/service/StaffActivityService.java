package com.ecommerce.product.service;
import com.ecommerce.common.outbox.OutboxPublisher;
import org.springframework.stereotype.Service;
import java.util.*;
@Service
public class StaffActivityService {
    private final OutboxPublisher outbox;
    public StaffActivityService(OutboxPublisher outbox){this.outbox=outbox;}
    public void record(String actor,String action,String targetType,Long targetId,String details){
        outbox.publish(UUID.randomUUID().toString(),"http://auth-service/internal/staff/events","STAFF_ACTIVITY",Map.of("actor",actor,"action",action,"targetType",targetType,"targetId",targetId,"details",details));
    }
}
