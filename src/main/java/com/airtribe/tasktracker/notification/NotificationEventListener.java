package com.airtribe.tasktracker.notification;

import com.airtribe.tasktracker.user.UserService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class NotificationEventListener {

    private final NotificationService notificationService;
    private final UserService userService;

    public NotificationEventListener(NotificationService notificationService, UserService userService) {
        this.notificationService = notificationService;
        this.userService = userService;
    }

    @EventListener
    public void onTaskAssigned(TaskAssignedEvent event) {
        notificationService.create(userService.findById(event.assigneeId()), NotificationType.TASK_ASSIGNED,
                Map.of("taskId", event.taskId().toString(), "taskTitle", event.taskTitle(),
                        "teamId", event.teamId().toString()));
    }

    @EventListener
    public void onTaskUpdated(TaskUpdatedEvent event) {
        notificationService.create(userService.findById(event.recipientId()), NotificationType.TASK_UPDATED,
                Map.of("taskId", event.taskId().toString(), "taskTitle", event.taskTitle(),
                        "teamId", event.teamId().toString()));
    }

    @EventListener
    public void onCommentAdded(CommentAddedEvent event) {
        notificationService.create(userService.findById(event.recipientId()), NotificationType.COMMENT_ADDED,
                Map.of("taskId", event.taskId().toString(), "taskTitle", event.taskTitle(),
                        "teamId", event.teamId().toString(), "commentAuthor", event.commentAuthorName()));
    }
}
