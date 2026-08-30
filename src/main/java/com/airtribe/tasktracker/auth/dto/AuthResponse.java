package com.airtribe.tasktracker.auth.dto;

import com.airtribe.tasktracker.user.dto.UserResponse;

public record AuthResponse(String accessToken, String refreshToken, UserResponse user) {
}
