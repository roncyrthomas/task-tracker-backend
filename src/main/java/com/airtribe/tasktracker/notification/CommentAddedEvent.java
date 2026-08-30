package com.airtribe.tasktracker.notification;

import java.util.UUID;

public record CommentAddedEvent(UUID taskId, String taskTitle, UUID teamId, UUID recipientId, String commentAuthorName) {
}
