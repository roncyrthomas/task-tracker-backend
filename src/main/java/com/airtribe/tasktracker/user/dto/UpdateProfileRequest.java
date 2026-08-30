package com.airtribe.tasktracker.user.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateProfileRequest(
        @NotBlank(message = "is required") String name,
        String avatarUrl
) {
}
