package com.airtribe.tasktracker.team;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TeamMembershipRepository extends JpaRepository<TeamMembership, UUID> {
    Optional<TeamMembership> findByTeamIdAndUserId(UUID teamId, UUID userId);
    List<TeamMembership> findByTeamId(UUID teamId);
    List<TeamMembership> findByUserId(UUID userId);
    boolean existsByTeamIdAndUserId(UUID teamId, UUID userId);
}
