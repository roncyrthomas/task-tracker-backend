package com.airtribe.tasktracker.team;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface InvitationRepository extends JpaRepository<Invitation, UUID> {
    Optional<Invitation> findByToken(String token);
    Optional<Invitation> findByTeamIdAndEmailAndStatus(UUID teamId, String email, InvitationStatus status);
}
