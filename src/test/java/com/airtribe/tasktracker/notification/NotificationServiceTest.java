package com.airtribe.tasktracker.notification;

import com.airtribe.tasktracker.common.exception.NotFoundException;
import com.airtribe.tasktracker.user.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock private NotificationRepository notificationRepository;
    @Mock private SimpMessagingTemplate messagingTemplate;

    private NotificationService service() {
        return new NotificationService(notificationRepository, messagingTemplate);
    }

    @Test
    void createSavesAndPushesToUserQueue() {
        UUID userId = UUID.randomUUID();
        User recipient = new User();
        recipient.setId(userId);
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        Notification notification = service().create(recipient, NotificationType.TASK_ASSIGNED, Map.of("taskId", "abc"));

        assertThat(notification.getType()).isEqualTo(NotificationType.TASK_ASSIGNED);
        assertThat(notification.isRead()).isFalse();
        verify(messagingTemplate).convertAndSendToUser(eq(userId.toString()), eq("/queue/notifications"), any(Object.class));
    }

    @Test
    void markReadThrowsNotFoundWhenMissingOrNotOwned() {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(notificationRepository.findByIdAndUserId(id, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().markRead(id, userId)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void markReadSetsReadTrueAndSaves() {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Notification notification = new Notification();
        notification.setId(id);
        notification.setRead(false);
        when(notificationRepository.findByIdAndUserId(id, userId)).thenReturn(Optional.of(notification));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        Notification result = service().markRead(id, userId);

        assertThat(result.isRead()).isTrue();
    }
}
