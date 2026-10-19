package com.ecommerce.ecommerce_system.service;

import com.ecommerce.ecommerce_system.model.StaffActivityLog;
import com.ecommerce.ecommerce_system.repository.StaffActivityLogRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/** Writes and reads the staff/admin audit trail. */
@Service
public class StaffActivityService {

    private final StaffActivityLogRepository repository;

    public StaffActivityService(StaffActivityLogRepository repository) {
        this.repository = repository;
    }

    public void record(String actorUsername, String action, String targetType, Long targetId, String details) {
        StaffActivityLog log = new StaffActivityLog();
        log.setActorUsername(actorUsername == null ? "system" : actorUsername);
        log.setAction(action);
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setDetails(details);
        log.setCreatedAt(java.time.LocalDateTime.now());
        repository.save(log);
    }

    public List<StaffActivityLog> recent() {
        return repository.findTop200ByOrderByCreatedAtDesc();
    }

    public List<StaffActivityLog> byActor(String username) {
        return repository.findByActorUsernameOrderByCreatedAtDesc(username);
    }

    public List<StaffActivityLog> byTarget(String targetType, Long targetId) {
        return repository.findTop100ByTargetTypeAndTargetIdOrderByCreatedAtDesc(targetType, targetId);
    }
}
