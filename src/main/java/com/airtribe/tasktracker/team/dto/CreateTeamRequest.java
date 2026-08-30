package com.airtribe.tasktracker.team.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateTeamRequest(@NotBlank(message = "is required") String name, String description) {
}
