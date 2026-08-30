package com.airtribe.tasktracker.user.dto;

import com.airtribe.tasktracker.user.User;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(UUID id, String name, String email, String avatarUrl, Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(), user.getName(), user.getEmail(), user.getAvatarUrl(), user.getCreatedAt());
    }
}
