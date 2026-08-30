package com.airtribe.tasktracker.task.dto;

import com.airtribe.tasktracker.task.TaskStatus;
import jakarta.validation.constraints.NotNull;

public record ChangeStatusRequest(@NotNull(message = "is required") TaskStatus status) {
}
