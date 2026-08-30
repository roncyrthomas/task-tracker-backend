package com.airtribe.tasktracker.team;

import com.airtribe.tasktracker.common.exception.NotFoundException;
import com.airtribe.tasktracker.user.User;
import com.airtribe.tasktracker.user.UserService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class TeamService {

    private final TeamRepository teamRepository;
    private final TeamMembershipRepository membershipRepository;
    private final UserService userService;

    public TeamService(TeamRepository teamRepository, TeamMembershipRepository membershipRepository,
                        UserService userService) {
        this.teamRepository = teamRepository;
        this.membershipRepository = membershipRepository;
        this.userService = userService;
    }

    public Team createTeam(UUID ownerId, String name, String description) {
        User owner = userService.findById(ownerId);
        Team team = new Team();
        team.setName(name);
        team.setDescription(description);
        team.setOwner(owner);
        team = teamRepository.save(team);

        TeamMembership membership = new TeamMembership();
        membership.setTeam(team);
        membership.setUser(owner);
        membership.setRole(TeamRole.OWNER);
        membership.setJoinedAt(Instant.now());
        membershipRepository.save(membership);

        return team;
    }

    public Team getTeam(UUID teamId) {
        return teamRepository.findById(teamId)
                .orElseThrow(() -> new NotFoundException("Team not found."));
    }

    public List<Team> listMyTeams(UUID userId) {
        List<UUID> teamIds = membershipRepository.findByUserId(userId).stream()
                .map(m -> m.getTeam().getId())
                .toList();
        return teamRepository.findAllById(teamIds);
    }

    public List<TeamMembership> listMembers(UUID teamId) {
        return membershipRepository.findByTeamId(teamId);
    }
}
