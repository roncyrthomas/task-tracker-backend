package com.airtribe.tasktracker.comment.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateCommentRequest(@NotBlank(message = "is required") String body) {
}
