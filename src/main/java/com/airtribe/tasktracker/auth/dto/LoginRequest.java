package com.airtribe.tasktracker.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(@NotBlank(message = "is required") String email,
                            @NotBlank(message = "is required") String password) {
}
