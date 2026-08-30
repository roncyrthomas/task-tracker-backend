package com.airtribe.tasktracker.task.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AssignTaskRequest(@NotNull(message = "is required") UUID assigneeId) {
}
