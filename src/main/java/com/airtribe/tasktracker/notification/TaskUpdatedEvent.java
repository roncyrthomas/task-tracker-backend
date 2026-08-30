package com.airtribe.tasktracker.notification;

import java.util.UUID;

public record TaskUpdatedEvent(UUID taskId, String taskTitle, UUID teamId, UUID recipientId) {
}
