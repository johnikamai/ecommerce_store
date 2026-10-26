package com.ecommerce.auth.service;

import com.ecommerce.auth.model.StaffNotification;
import com.ecommerce.auth.model.User;
import com.ecommerce.auth.repo.StaffNotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Staff-facing notifications (task assignments, review decisions, approval
 * outcomes). In-app is the source of truth; a best-effort email is also sent when
 * the mail gateway is configured, reusing the existing {@link MailService}.
 */
@Service
public class StaffNotificationService {

    private final StaffNotificationRepository repository;
    private final MailService mailService;

    public StaffNotificationService(StaffNotificationRepository repository, MailService mailService) {
        this.repository = repository;
        this.mailService = mailService;
    }

    public StaffNotification notify(User user, String message, String type) {
        StaffNotification notification = new StaffNotification();
        notification.setUser(user);
        notification.setMessage(message);
        notification.setType(type);
        notification.setIsRead(false);
        notification.setSentAt(LocalDateTime.now());
        StaffNotification saved = repository.save(notification);

        try {
            if (mailService.isConfigured() && user.getEmail() != null) {
                mailService.sendHtmlAsync(user.getEmail(), "ShopEase staff update",
                        "<p>" + message + "</p>");
            }
        } catch (Exception ignored) {
            // Email is best-effort; the in-app notification is what matters.
        }
        return saved;
    }

    public List<StaffNotification> forUser(Long userId) {
        return repository.findTop100ByUserIdOrderBySentAtDesc(userId);
    }

    public long unreadCount(Long userId) {
        return repository.countByUserIdAndIsReadFalse(userId);
    }

    @Transactional
    public int markAllRead(Long userId) {
        return repository.markAllRead(userId);
    }
}
