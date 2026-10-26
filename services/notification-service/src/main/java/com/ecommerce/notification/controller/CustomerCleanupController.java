package com.ecommerce.notification.controller;
import com.ecommerce.notification.repo.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;
/** Protected by InternalServiceFilter's constant-time X-Service-Key verification;
 * no public Gateway mapping. Retains watcher tombstones against delayed events. */
@RestController @RequestMapping("/internal/customers")
public class CustomerCleanupController {
    private final NotificationRepository notifications;private final WatcherRepository watchers;
    private final EmailDeliveryRepository emails;private final SmsDeliveryRepository sms;
    private final InboxRepository inbox;
    public CustomerCleanupController(NotificationRepository n,WatcherRepository w,EmailDeliveryRepository e,SmsDeliveryRepository s,InboxRepository i){notifications=n;watchers=w;emails=e;sms=s;inbox=i;}
    @PostMapping("/{id}/cleanup") @Transactional
    public Map<String,Boolean> cleanup(@PathVariable Long id){
        // Retain a durable marker even if the first wishlist event has not arrived.
        var marker=new com.ecommerce.notification.model.InboxReceipt();marker.id="customer-deleted:"+id;inbox.saveAndFlush(marker);
        watchers.findAll().stream().filter(w->id.equals(w.customerId)).forEach(w->{w.active=false;w.sourceVersion=Long.MAX_VALUE;watchers.save(w);});
        emails.deleteByCustomerId(id);sms.deleteByCustomerId(id);notifications.deleteByCustomerId(id);return Map.of("cleaned",true);
    }
}
