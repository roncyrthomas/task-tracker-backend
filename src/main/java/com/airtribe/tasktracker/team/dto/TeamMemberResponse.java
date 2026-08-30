package com.airtribe.tasktracker.team.dto;

import com.airtribe.tasktracker.team.TeamMembership;
import com.airtribe.tasktracker.team.TeamRole;

import java.time.Instant;
import java.util.UUID;

public record TeamMemberResponse(UUID userId, String name, String email, String avatarUrl,
                                  TeamRole role, Instant joinedAt) {
    public static TeamMemberResponse from(TeamMembership membership) {
        return new TeamMemberResponse(
                membership.getUser().getId(),
                membership.getUser().getName(),
                membership.getUser().getEmail(),
                membership.getUser().getAvatarUrl(),
                membership.getRole(),
                membership.getJoinedAt());
    }
}
