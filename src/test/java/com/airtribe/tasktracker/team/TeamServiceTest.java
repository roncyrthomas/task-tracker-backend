package com.airtribe.tasktracker.team;

import com.airtribe.tasktracker.user.User;
import com.airtribe.tasktracker.user.UserService;
import com.airtribe.tasktracker.common.exception.NotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TeamServiceTest {

    @Mock private TeamRepository teamRepository;
    @Mock private TeamMembershipRepository membershipRepository;
    @Mock private UserService userService;

    private TeamService teamService() {
        return new TeamService(teamRepository, membershipRepository, userService);
    }

    @Test
    void createTeamSavesTeamAndOwnerMembership() {
        UUID ownerId = UUID.randomUUID();
        User owner = new User();
        owner.setId(ownerId);
        when(userService.findById(ownerId)).thenReturn(owner);
        when(teamRepository.save(any(Team.class))).thenAnswer(inv -> inv.getArgument(0));
        when(membershipRepository.save(any(TeamMembership.class))).thenAnswer(inv -> inv.getArgument(0));

        Team team = teamService().createTeam(ownerId, "Engineering", "The eng team");

        assertThat(team.getName()).isEqualTo("Engineering");
        assertThat(team.getOwner()).isSameAs(owner);

        ArgumentCaptor<TeamMembership> captor = ArgumentCaptor.forClass(TeamMembership.class);
        verify(membershipRepository).save(captor.capture());
        assertThat(captor.getValue().getRole()).isEqualTo(TeamRole.OWNER);
        assertThat(captor.getValue().getUser()).isSameAs(owner);
    }

    @Test
    void getTeamThrowsNotFoundWhenMissing() {
        UUID teamId = UUID.randomUUID();
        when(teamRepository.findById(teamId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> teamService().getTeam(teamId)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void listMyTeamsReturnsTeamsFromMemberships() {
        UUID userId = UUID.randomUUID();
        Team team = new Team();
        team.setId(UUID.randomUUID());
        TeamMembership membership = new TeamMembership();
        membership.setTeam(team);
        when(membershipRepository.findByUserId(userId)).thenReturn(List.of(membership));
        when(teamRepository.findAllById(List.of(team.getId()))).thenReturn(List.of(team));

        List<Team> result = teamService().listMyTeams(userId);

        assertThat(result).containsExactly(team);
    }
}
