package com.airtribe.tasktracker.ai.dto;

import jakarta.validation.constraints.NotBlank;

public record GenerateDescriptionRequest(@NotBlank(message = "is required") String title, String notes) {
}
