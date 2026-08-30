package com.airtribe.tasktracker.comment.dto;

import com.airtribe.tasktracker.comment.Comment;

import java.time.Instant;
import java.util.UUID;

public record CommentResponse(UUID id, UUID taskId, UUID authorId, String authorName, String body, Instant createdAt) {
    public static CommentResponse from(Comment comment) {
        return new CommentResponse(
                comment.getId(), comment.getTask().getId(), comment.getAuthor().getId(),
                comment.getAuthor().getName(), comment.getBody(), comment.getCreatedAt());
    }
}
