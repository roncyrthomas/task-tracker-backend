package com.airtribe.tasktracker.team;

import com.airtribe.tasktracker.common.exception.ForbiddenException;
import com.airtribe.tasktracker.user.User;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Service
public class TeamMembershipService {

    private final TeamMembershipRepository membershipRepository;

    public TeamMembershipService(TeamMembershipRepository membershipRepository) {
        this.membershipRepository = membershipRepository;
    }

    public TeamMembership requireMember(UUID teamId, UUID userId) {
        return membershipRepository.findByTeamIdAndUserId(teamId, userId)
                .orElseThrow(() -> new ForbiddenException("You are not a member of this team."));
    }

    public TeamMembership requireRole(UUID teamId, UUID userId, Set<TeamRole> allowedRoles) {
        TeamMembership membership = requireMember(teamId, userId);
        if (!allowedRoles.contains(membership.getRole())) {
            throw new ForbiddenException("You do not have permission to perform this action.");
        }
        return membership;
    }

    public TeamMembership addMember(Team team, User user, TeamRole role) {
        TeamMembership membership = new TeamMembership();
        membership.setTeam(team);
        membership.setUser(user);
        membership.setRole(role);
        membership.setJoinedAt(Instant.now());
        return membershipRepository.save(membership);
    }
}
