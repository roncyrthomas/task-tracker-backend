package com.airtribe.tasktracker.task.dto;

import com.airtribe.tasktracker.task.TaskPriority;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;

public record CreateTaskRequest(
        @NotBlank(message = "is required") String title,
        String description,
        TaskPriority priority,
        Instant dueDate
) {
}
