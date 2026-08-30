package com.airtribe.tasktracker.notification;

import com.airtribe.tasktracker.common.exception.NotFoundException;
import com.airtribe.tasktracker.notification.dto.NotificationResponse;
import com.airtribe.tasktracker.user.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public NotificationService(NotificationRepository notificationRepository, SimpMessagingTemplate messagingTemplate) {
        this.notificationRepository = notificationRepository;
        this.messagingTemplate = messagingTemplate;
    }

    public Notification create(User recipient, NotificationType type, Map<String, Object> payload) {
        Notification notification = new Notification();
        notification.setUser(recipient);
        notification.setType(type);
        notification.setPayload(payload);
        notification.setRead(false);
        notification = notificationRepository.save(notification);

        messagingTemplate.convertAndSendToUser(
                recipient.getId().toString(), "/queue/notifications", NotificationResponse.from(notification));
        return notification;
    }

    public Page<Notification> list(UUID userId, Pageable pageable) {
        return notificationRepository.findByUserId(userId, pageable);
    }

    public Notification markRead(UUID notificationId, UUID userId) {
        Notification notification = notificationRepository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new NotFoundException("Notification not found."));
        notification.setRead(true);
        return notificationRepository.save(notification);
    }
}
