package com.ecommerce.auth.controller;
import com.ecommerce.auth.repo.*;
import com.ecommerce.auth.model.InboxReceipt;
import com.ecommerce.auth.service.*;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController @RequestMapping("/internal/staff")
public class StaffEventController {
    private final InboxRepository inbox;private final UserRepository users;
    private final StaffService staff;private final StaffTaskService tasks;private final StaffActivityService activities;private final StaffNotificationService notifications;
    public StaffEventController(InboxRepository inbox,UserRepository users,StaffService staff,StaffTaskService tasks,StaffActivityService activities,StaffNotificationService notifications){this.inbox=inbox;this.users=users;this.staff=staff;this.tasks=tasks;this.activities=activities;this.notifications=notifications;}
    @GetMapping("/summary") public Object summary(){return Map.of("staff",staff.summary(),"tasks",tasks.adminSummary());}
    @PostMapping("/events") @Transactional
    public void receive(@RequestBody JsonNode event){
        String id=event.path("id").asText();if(id.isBlank()||id.length()>128)throw new IllegalArgumentException("Valid event ID required");if(inbox.existsById(id))return;
        var receipt=new InboxReceipt();receipt.id=id;inbox.saveAndFlush(receipt);var body=event.path("payload");
        switch(event.path("kind").asText()){
            case "STAFF_ACTIVITY" -> activities.record(body.path("actor").asText(),body.path("action").asText(),body.path("targetType").asText(),body.path("targetId").asLong(),body.path("details").asText());
            case "STAFF_NOTIFICATION" -> notifications.notify(users.findById(body.path("userId").asLong()).orElseThrow(),body.path("message").asText(),body.path("type").asText());
            default -> throw new IllegalArgumentException("Unknown staff event type");
        }
    }
}
