package com.airtribe.tasktracker.team.dto;

import com.airtribe.tasktracker.team.Team;

import java.time.Instant;
import java.util.UUID;

public record TeamResponse(UUID id, String name, String description, UUID ownerId, Instant createdAt) {
    public static TeamResponse from(Team team) {
        return new TeamResponse(team.getId(), team.getName(), team.getDescription(),
                team.getOwner().getId(), team.getCreatedAt());
    }
}
