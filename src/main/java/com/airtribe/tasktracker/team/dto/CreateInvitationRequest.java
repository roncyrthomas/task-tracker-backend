package com.airtribe.tasktracker.team.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record CreateInvitationRequest(
        @NotBlank(message = "is required") @Email(message = "must be a valid email") String email
) {
}
