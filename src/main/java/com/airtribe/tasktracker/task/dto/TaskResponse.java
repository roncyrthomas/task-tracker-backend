package com.airtribe.tasktracker.task.dto;

import com.airtribe.tasktracker.task.Task;
import com.airtribe.tasktracker.task.TaskPriority;
import com.airtribe.tasktracker.task.TaskStatus;

import java.time.Instant;
import java.util.UUID;

public record TaskResponse(UUID id, UUID teamId, String title, String description, TaskStatus status,
                            TaskPriority priority, Instant dueDate, UUID createdBy, UUID assigneeId,
                            Instant createdAt, Instant updatedAt) {
    public static TaskResponse from(Task task) {
        return new TaskResponse(
                task.getId(), task.getTeam().getId(), task.getTitle(), task.getDescription(),
                task.getStatus(), task.getPriority(), task.getDueDate(), task.getCreatedBy().getId(),
                task.getAssignee() == null ? null : task.getAssignee().getId(),
                task.getCreatedAt(), task.getUpdatedAt());
    }
}
