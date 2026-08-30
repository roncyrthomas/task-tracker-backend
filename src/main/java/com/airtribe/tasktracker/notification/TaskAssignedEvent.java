package com.airtribe.tasktracker.notification;

import java.util.UUID;

public record TaskAssignedEvent(UUID taskId, String taskTitle, UUID teamId, UUID assigneeId) {
}
