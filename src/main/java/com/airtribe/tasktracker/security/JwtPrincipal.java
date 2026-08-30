package com.airtribe.tasktracker.security;

import java.util.UUID;

public record JwtPrincipal(UUID userId, String email) {
}
