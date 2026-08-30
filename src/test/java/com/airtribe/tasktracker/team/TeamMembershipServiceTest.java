package com.airtribe.tasktracker.team;

import com.airtribe.tasktracker.common.exception.ForbiddenException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TeamMembershipServiceTest {

    @Mock private TeamMembershipRepository membershipRepository;
    @InjectMocks private TeamMembershipService teamMembershipService;

    @Test
    void requireMemberThrowsWhenNotAMember() {
        UUID teamId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(membershipRepository.findByTeamIdAndUserId(teamId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> teamMembershipService.requireMember(teamId, userId))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void requireRoleThrowsWhenRoleNotAllowed() {
        UUID teamId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        TeamMembership membership = new TeamMembership();
        membership.setRole(TeamRole.MEMBER);
        when(membershipRepository.findByTeamIdAndUserId(teamId, userId)).thenReturn(Optional.of(membership));

        assertThatThrownBy(() -> teamMembershipService.requireRole(teamId, userId, Set.of(TeamRole.OWNER, TeamRole.ADMIN)))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void requireRoleSucceedsWhenRoleAllowed() {
        UUID teamId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        TeamMembership membership = new TeamMembership();
        membership.setRole(TeamRole.ADMIN);
        when(membershipRepository.findByTeamIdAndUserId(teamId, userId)).thenReturn(Optional.of(membership));

        TeamMembership result = teamMembershipService.requireRole(teamId, userId, Set.of(TeamRole.OWNER, TeamRole.ADMIN));

        assertThat(result).isSameAs(membership);
    }
}
