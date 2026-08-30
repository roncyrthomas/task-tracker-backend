package com.airtribe.tasktracker.team.dto;

import com.airtribe.tasktracker.team.Invitation;

import java.time.Instant;
import java.util.UUID;

public record InvitationResponse(UUID id, UUID teamId, String email, String token, String status, Instant expiresAt) {
    public static InvitationResponse from(Invitation invitation) {
        return new InvitationResponse(
                invitation.getId(), invitation.getTeam().getId(), invitation.getEmail(),
                invitation.getToken(), invitation.getStatus().name(), invitation.getExpiresAt());
    }
}
